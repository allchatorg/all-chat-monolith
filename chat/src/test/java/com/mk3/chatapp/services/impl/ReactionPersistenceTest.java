package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.mappers.ReactionMapper;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.*;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.ReactionRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JPA transactions/locks and membership reloads, without external infrastructure. */
@ExtendWith(MockitoExtension.class)
class ReactionPersistenceTest {
    private static EntityManagerFactory entityManagerFactory;
    private static EntityManager entityManager;
    private static TransactionTemplate transactions;
    private static MessageRepository messages;
    private static ReactionRepository reactions;
    private static UserRepository users;
    private static UserChatRoomRepository roomMembers;

    @Mock private WebSocketBroadcastService sockets;
    @Mock private RoomActivityService activity;
    @Mock private ChatRoomService rooms;
    @Mock private PrivateChatService privateChats;
    @Mock private SecurityService security;
    private ReactionServiceImpl service;
    private List<User> members;
    private Long messageId;
    private Long roomId;

    @BeforeAll
    static void startDatabase() {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(
                "jdbc:h2:mem:reactions;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", ""));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(
                User.class.getName(), Message.class.getName(), Reaction.class.getName(), ChatRoom.class.getName(),
                UserChatRoom.class.getName(), MessageEditHistory.class.getName(), Attachment.class.getName(),
                AttachmentType.class.getName(), Tag.class.getName(), Ban.class.getName(), UsernameHistory.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.show_sql", "false"));
        factory.afterPropertiesSet();
        entityManagerFactory = Objects.requireNonNull(factory.getObject());
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        transactions = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(entityManager);
        messages = repositories.getRepository(MessageRepository.class);
        reactions = repositories.getRepository(ReactionRepository.class);
        users = repositories.getRepository(UserRepository.class);
        roomMembers = repositories.getRepository(UserChatRoomRepository.class);
        // User's existing read-only formulas refer to the ads module's table.
        transactions.executeWithoutResult(status -> entityManager.createNativeQuery(
                "CREATE TABLE ads (id bigint primary key, owner_id bigint, total_cost double precision)").executeUpdate());
    }

    @AfterAll
    static void closeDatabase() {
        if (entityManagerFactory != null) entityManagerFactory.close();
    }

    @BeforeEach
    void setUp() {
        ReactionMapper mapper = Mappers.getMapper(ReactionMapper.class);
        ReflectionTestUtils.setField(mapper, "userMapper", Mappers.getMapper(UserMapper.class));
        service = new ReactionServiceImpl(reactions, messages,
                new ProReactionService(new ProBadgeService(users, null, entityManager)), rooms,
                privateChats, security, activity, sockets, mapper, roomMembers);
        transactions.executeWithoutResult(status -> {
            String run = UUID.randomUUID().toString();
            members = IntStream.range(0, 6).mapToObj(index -> {
                User user = User.builder().username(run + index).role(Role.USER).showProBadge(false)
                        .proPaidThrough(Instant.now().plusSeconds(3600)).build();
                entityManager.persist(user);
                return user;
            }).toList();
            ChatRoom room = ChatRoom.builder().name(run).build();
            entityManager.persist(room);
            roomId = room.getId();
            Message message = Message.builder().chatRoom(room).content("reactions belong to this message").build();
            entityManager.persist(message);
            messageId = message.getId();
        });
    }

    @Test
    void standaloneStickerAndReplySurviveSeparateTransactionReloads() {
        Long stickerMessageId = transactions.execute(status -> {
            var message = Message.builder().chatRoom(entityManager.find(ChatRoom.class, roomId))
                    .sender(entityManager.find(User.class, members.getFirst().getId()))
                    .content("").stickerId("pepe").build();
            entityManager.persist(message);
            return message.getId();
        });
        Long replyId = transactions.execute(status -> {
            var parent = messages.findById(stickerMessageId).orElseThrow();
            assertThat(parent.getStickerId()).isEqualTo("pepe");
            assertThat(parent.getContent()).isEmpty();
            var reply = Message.builder().chatRoom(parent.getChatRoom()).sender(parent.getSender())
                    .content("reply to sticker").replyTo(parent).build();
            entityManager.persist(reply);
            return reply.getId();
        });
        transactions.executeWithoutResult(status -> {
            var reply = messages.findById(replyId).orElseThrow();
            assertThat(reply.getStickerId()).isNull();
            assertThat(reply.getReplyTo().getStickerId()).isEqualTo("pepe");
            assertThat(messages.findById(messageId).orElseThrow().getStickerId()).isNull();
        });
    }

    @Test
    void concurrentFirstAddsCreateOneReactionAndOneMembershipPerUser() throws Exception {
        // Two requests per user race for the initially absent reaction row.
        runConcurrently(12, index -> transactions.executeWithoutResult(status ->
                service.addReaction(members.get(index % members.size()), messageId, "allchat:pepe", "allchat:pepe")));

        transactions.executeWithoutResult(status -> {
            Reaction reaction = reactions.findByMessageIdAndEmoji(messageId, "allchat:pepe").orElseThrow();
            assertThat(reaction.getUsers()).extracting(User::getId)
                    .containsExactlyInAnyOrderElementsOf(members.stream().map(User::getId).toList());
            assertThat(entityManager.createQuery("select count(r) from Reaction r where r.message.id = :id", Long.class)
                    .setParameter("id", messageId).getSingleResult()).isEqualTo(1);
        });
        verify(activity, times(6)).incrementReactionCount(roomId, messageId);
        verify(sockets, times(6)).broadcastToChatRoom(anyString(), any());
    }

    @Test
    void concurrentDuplicateRemovalsDeleteOnceAndDecrementOnce() throws Exception {
        User member = members.getFirst();
        transactions.executeWithoutResult(status -> service.addReaction(member, messageId, "allchat:pepe", "allchat:pepe"));
        clearInvocations(activity, sockets);
        runConcurrently(8, index -> transactions.executeWithoutResult(status ->
                service.removeReaction(member, messageId, "allchat:pepe", "allchat:pepe")));
        transactions.executeWithoutResult(status ->
                assertThat(reactions.findByMessageIdAndEmoji(messageId, "allchat:pepe")).isEmpty());
        verify(activity).decrementReactionCount(roomId, messageId);
        verify(sockets).broadcastToChatRoom(anyString(), any());
    }

    @Test
    void concurrentAddRemoveCyclesKeepPersistedMembershipConsistent() throws Exception {
        runConcurrently(members.size(), index -> {
            for (int cycle = 0; cycle < 3; cycle++) {
                transactions.executeWithoutResult(status -> service.addReaction(
                        members.get(index), messageId, "allchat:wojak", "allchat:wojak"));
                transactions.executeWithoutResult(status -> service.removeReaction(
                        members.get(index), messageId, "allchat:wojak", "allchat:wojak"));
            }
        });
        transactions.executeWithoutResult(status ->
                assertThat(reactions.findByMessageIdAndEmoji(messageId, "allchat:wojak")).isEmpty());
        verify(activity, times(18)).incrementReactionCount(roomId, messageId);
        verify(activity, times(18)).decrementReactionCount(roomId, messageId);
    }

    @Test
    void limitingUserPreviewThenReloadingDoesNotRemovePersistedMembership() {
        members.forEach(member -> transactions.executeWithoutResult(status ->
                service.addReaction(member, messageId, "allchat:pepe", "allchat:pepe")));
        when(security.getCurrentUser()).thenReturn(members.getFirst());
        transactions.executeWithoutResult(status -> {
            var details = service.findByMessageIdAndEmoji(messageId, "allchat:pepe", 2);
            assertThat(details.users()).hasSize(2);
            assertThat(details.usersCount()).isEqualTo(6);
        });
        // A separate transaction/EntityManager reloads the actual join table.
        transactions.executeWithoutResult(status -> {
            assertThat(reactions.findByMessageIdAndEmoji(messageId, "allchat:pepe").orElseThrow().getUsers()).hasSize(6);
            assertThat(service.findByMessageIdAndEmoji(messageId, "allchat:pepe", null).users()).hasSize(6);
        });
    }

    @Test
    void rolledBackMutationPublishesNothingAndLeavesNoReaction() {
        transactions.executeWithoutResult(status -> {
            service.addReaction(members.getFirst(), messageId, "allchat:pepe", "allchat:pepe");
            verifyNoInteractions(activity, sockets);
            status.setRollbackOnly();
        });
        transactions.executeWithoutResult(status ->
                assertThat(reactions.findByMessageIdAndEmoji(messageId, "allchat:pepe")).isEmpty());
        verifyNoInteractions(activity, sockets);
    }

    @Test
    void socketFailureCannotUndoCommittedReactionOrFailTheRequest() {
        doThrow(new IllegalStateException("Socket unavailable")).when(sockets).broadcastToChatRoom(anyString(), any());
        assertThatCode(() -> transactions.executeWithoutResult(status ->
                service.addReaction(members.getFirst(), messageId, "allchat:pepe", "allchat:pepe")))
                .doesNotThrowAnyException();
        transactions.executeWithoutResult(status ->
                assertThat(reactions.findByMessageIdAndEmoji(messageId, "allchat:pepe").orElseThrow().getUsers()).hasSize(1));
        verify(activity).incrementReactionCount(roomId, messageId);
    }

    @Test
    void activityFailureStillDeliversAndPreservesCommittedReaction() {
        doThrow(new IllegalStateException("Redis unavailable")).when(activity).incrementReactionCount(roomId, messageId);
        assertThatCode(() -> transactions.executeWithoutResult(status ->
                service.addReaction(members.getFirst(), messageId, "allchat:pepe", "allchat:pepe")))
                .doesNotThrowAnyException();
        transactions.executeWithoutResult(status ->
                assertThat(reactions.findByMessageIdAndEmoji(messageId, "allchat:pepe").orElseThrow().getUsers()).hasSize(1));
        verify(sockets).broadcastToChatRoom(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"banned", "deleted", "expired"})
    void entitlementComesFromCurrentDatabaseStateInsteadOfCachedUser(String state) {
        User stale = members.getFirst();
        transactions.executeWithoutResult(status -> {
            String assignment = switch (state) {
                case "banned" -> "banned = true";
                case "deleted" -> "deleted = true";
                default -> "pro_paid_through = TIMESTAMP '2000-01-01 00:00:00'";
            };
            entityManager.createNativeQuery("update chat_user set " + assignment + " where id = :id")
                    .setParameter("id", stale.getId()).executeUpdate();
        });
        assertThat(stale.isProActive()).isTrue();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                service.addReaction(stale, messageId, "allchat:pepe", "allchat:pepe")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(activity, sockets);
    }

    private void runConcurrently(int count, java.util.function.IntConsumer action) throws Exception {
        CyclicBarrier start = new CyclicBarrier(count);
        try (ExecutorService executor = Executors.newFixedThreadPool(count)) {
            var pending = IntStream.range(0, count).mapToObj(index -> executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                action.accept(index);
                return null;
            })).toList();
            for (Future<?> future : pending) future.get(30, TimeUnit.SECONDS);
        }
    }
}
