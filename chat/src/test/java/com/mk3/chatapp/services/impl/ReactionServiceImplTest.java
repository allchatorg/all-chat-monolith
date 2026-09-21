package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.ReactionDetailsDTO;
import com.mk3.chatapp.dtos.responses.ReactionSocketResponse;
import com.mk3.chatapp.dtos.responses.UserMinimalDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.ReactionType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.mappers.ReactionMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Reaction;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.ReactionRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactionServiceImplTest {
    @Mock private MessageRepository messages;
    @Mock private ReactionRepository reactions;
    @Mock private UserRepository users;
    @Mock private UserChatRoomRepository roomMembers;
    @Mock private WebSocketBroadcastService sockets;
    @Mock private ReactionMapper mapper;
    @Mock private ChatRoomService rooms;
    @Mock private PrivateChatService privateChats;
    @Mock private SecurityService security;
    @Mock private RoomActivityService activity;
    private ReactionServiceImpl service;
    private User user;
    private ChatRoom room;
    private Message message;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("member").role(Role.USER).showProBadge(false).build();
        room = ChatRoom.builder().id(5L).name("public-room").build();
        message = Message.builder().id(11L).chatRoom(room).content("hello").build();
        service = new ReactionServiceImpl(reactions, messages,
                new ProReactionService(new ProBadgeService(users, null, null)), rooms,
                privateChats, security, activity, sockets, mapper, roomMembers);
    }

    private void lockMessage() {
        when(messages.findByIdForReactionUpdate(message.getId())).thenReturn(Optional.of(message));
    }

    private void activePro() {
        when(users.findEligibleProPaidThrough(user.getId())).thenReturn(Optional.of(Instant.now().plusSeconds(3600)));
    }

    private Reaction reaction(String emoji, User... members) {
        return Reaction.builder().id(7L).message(message).emoji(emoji).emojiId(emoji)
                .users(new HashSet<>(List.of(members))).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wojak", "soyjak", "chud", "chad-1", "chad-2", "virgin", "doomer", "coomer",
            "bloomer", "zoomer", "npc", "grug", "pepe", "apu-apustaja", "honkler", "spurdo", "gondola"})
    void allSeventeenCharactersUseCanonicalIdentityAndFreshEntitlementWithHiddenBadge(String id) {
        lockMessage();
        activePro();
        String token = "allchat:" + id;

        service.addReaction(user, message.getId(), token, token);

        var saved = ArgumentCaptor.forClass(Reaction.class);
        verify(reactions).save(saved.capture());
        assertThat(saved.getValue().getEmoji()).isEqualTo(token);
        assertThat(saved.getValue().getEmojiId()).isEqualTo(token);
        assertThat(saved.getValue().getUsers()).containsExactly(user);
        assertThat(user.isProBadgeVisible()).isFalse();
        verify(users).findEligibleProPaidThrough(user.getId());
        verify(activity).incrementReactionCount(room.getId(), message.getId());
        verify(rooms).validateUserCanJoinChatRoom(user, room);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void freeAndExpiredProCannotAddEvenWithForgedCachedEntitlement(boolean expired) {
        lockMessage();
        user.setShowProBadge(true);
        user.setProPaidThrough(Instant.now().plusSeconds(3600));
        when(users.findEligibleProPaidThrough(user.getId())).thenReturn(expired
                ? Optional.of(Instant.now().minusSeconds(1)) : Optional.empty());

        assertThatThrownBy(() -> service.addReaction(user, message.getId(), "allchat:pepe", "allchat:pepe"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(reactions, activity, sockets);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "allchat:pepe|pepe", "🐸|allchat:pepe", "allchat:pepe|allchat:wojak",
            "allchat:unknown|allchat:unknown", "allchat:PEPE|allchat:PEPE", "allchat:../pepe|allchat:../pepe",
            "allchat:|allchat:", "allchat:pepe|NULL", "NULL|allchat:pepe"
    }, delimiter = '|', nullValues = "NULL")
    void mismatchedAndUnknownCustomTokensAreRejectedForAddAndRemove(String emoji, String emojiId) {
        lockMessage();
        assertThatThrownBy(() -> service.addReaction(user, message.getId(), emoji, emojiId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.removeReaction(user, message.getId(), emoji, emojiId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(users, reactions, activity, sockets);
    }

    @Test
    void unicodeReactionDoesNotCheckProAndLocksMessageBeforeLookingUpReaction() {
        lockMessage();
        service.addReaction(user, message.getId(), "👍", "+1");
        var order = inOrder(messages, reactions);
        order.verify(messages).findByIdForReactionUpdate(message.getId());
        order.verify(reactions).findByMessageIdAndEmoji(message.getId(), "👍");
        verifyNoInteractions(users);
        verify(activity).incrementReactionCount(room.getId(), message.getId());
    }

    @Test
    void repeatAddDoesNotIncrementOrBroadcastButStillChecksCurrentPro() {
        lockMessage();
        activePro();
        var existing = reaction("allchat:pepe", user);
        when(reactions.findByMessageIdAndEmoji(message.getId(), existing.getEmoji())).thenReturn(Optional.of(existing));
        service.addReaction(user, message.getId(), existing.getEmoji(), existing.getEmojiId());
        verify(users).findEligibleProPaidThrough(user.getId());
        verify(reactions, never()).save(any());
        verifyNoInteractions(activity, sockets);
    }

    @Test
    void expiredUserCanRemoveUsingUserIdEvenWhenSessionAndPersistedUserAreDifferentObjects() {
        lockMessage();
        User persisted = User.builder().id(user.getId()).username(user.getUsername()).role(Role.USER).build();
        var existing = reaction("allchat:pepe", persisted);
        when(reactions.findByMessageIdAndEmoji(message.getId(), existing.getEmoji())).thenReturn(Optional.of(existing));
        service.removeReaction(user, message.getId(), existing.getEmoji(), existing.getEmojiId());
        assertThat(existing.getUsers()).isEmpty();
        verify(reactions).delete(existing);
        verify(activity).decrementReactionCount(room.getId(), message.getId());
        verifyNoInteractions(users);
    }

    @Test
    void removingOneUserPreservesOthersAndDuplicateRemovalDoesNotChangeCounters() {
        lockMessage();
        User other = User.builder().id(2L).username("other").role(Role.USER).build();
        var existing = reaction("allchat:pepe", user, other);
        when(reactions.findByMessageIdAndEmoji(message.getId(), existing.getEmoji())).thenReturn(Optional.of(existing));
        service.removeReaction(user, message.getId(), existing.getEmoji(), existing.getEmojiId());
        service.removeReaction(user, message.getId(), existing.getEmoji(), existing.getEmojiId());
        assertThat(existing.getUsers()).containsExactly(other);
        verify(reactions).save(existing);
        verify(activity).decrementReactionCount(room.getId(), message.getId());
        verify(sockets).broadcastToChatRoom(eq(room.getName()), any());
        verifyNoInteractions(users);
    }

    @Test
    void removingMissingReactionDoesNotChangeCounters() {
        lockMessage();
        service.removeReaction(user, message.getId(), "👍", "+1");
        verifyNoInteractions(activity, sockets, users);
        verify(reactions, never()).save(any());
        verify(reactions, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(ChatRoomType.class)
    void sendsCanonicalLiveUpdateToPublicTopicOrPrivateMembers(ChatRoomType type) {
        lockMessage();
        activePro();
        room.setType(type);
        User other = User.builder().id(2L).username("other").role(Role.USER).build();
        if (type == ChatRoomType.PRIVATE) {
            room.setName(null);
            user.setRole(Role.MODERATOR);
            when(roomMembers.findByChatRoom(room)).thenReturn(List.of(
                    UserChatRoom.builder().user(user).chatRoom(room).build(),
                    UserChatRoom.builder().user(other).chatRoom(room).build()));
        }
        service.addReaction(user, message.getId(), "allchat:pepe", "allchat:pepe");
        var payload = ArgumentCaptor.forClass(WebSocketMessage.class);
        if (type == ChatRoomType.PRIVATE) {
            verify(privateChats).assertMember(user, room);
            verify(sockets).sendPrivateToUser(eq(user.getId()), payload.capture());
            verify(sockets).sendPrivateToUser(eq(other.getId()), any());
            verify(sockets, never()).broadcastToChatRoom(any(), any());
        } else {
            verify(sockets).broadcastToChatRoom(eq(room.getName()), payload.capture());
        }
        assertThat(payload.getValue().getType()).isEqualTo(WebSocketMessageType.MESSAGE_REACTION_UPDATE);
        var update = (ReactionSocketResponse) payload.getValue().getData();
        assertThat(update.emoji()).isEqualTo("allchat:pepe");
        assertThat(update.emojiId()).isEqualTo("allchat:pepe");
        assertThat(update.responseType()).isEqualTo(ReactionType.ADD);
    }

    @ParameterizedTest
    @EnumSource(ChatRoomType.class)
    void inaccessibleRoomsRejectAddRemoveAndDetailReadsBeforeAccessingMembership(ChatRoomType type) {
        lockMessage();
        when(messages.findById(message.getId())).thenReturn(Optional.of(message));
        when(security.getCurrentUser()).thenReturn(user);
        room.setType(type);
        if (type == ChatRoomType.PRIVATE) {
            doThrow(new AccessDeniedException("Not a member")).when(privateChats).assertMember(user, room);
        } else {
            doThrow(new AccessDeniedException("Insufficient room role")).when(rooms).validateUserCanJoinChatRoom(user, room);
        }
        assertThatThrownBy(() -> service.addReaction(user, message.getId(), "👍", "+1")).isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.removeReaction(user, message.getId(), "👍", "+1")).isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.findByMessageIdAndEmoji(message.getId(), "👍", null)).isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(reactions, users, activity, sockets);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletedAndQuarantinedMessagesRejectMutationsAndReads(boolean quarantined) {
        lockMessage();
        message.setDeleted(!quarantined);
        message.setQuarantined(quarantined);
        when(messages.findById(message.getId())).thenReturn(Optional.of(message));
        when(security.getCurrentUser()).thenReturn(user);
        assertThatThrownBy(() -> service.addReaction(user, message.getId(), "👍", "+1")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.removeReaction(user, message.getId(), "👍", "+1")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findByMessageIdAndEmoji(message.getId(), "👍", null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(reactions, activity, sockets);
    }

    @Test
    void archivedRoomCannotAddOrRemoveReactions() {
        lockMessage();
        room.setArchived(true);
        doThrow(new IllegalArgumentException("Archived room")).when(rooms).validateRoomIsNotArchived(room, "react to messages");
        assertThatThrownBy(() -> service.addReaction(user, message.getId(), "👍", "+1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.removeReaction(user, message.getId(), "👍", "+1")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reactions, activity, sockets);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10})
    void limitedDetailReadPreservesFullMembershipAndCount(int limit) {
        User other = User.builder().id(2L).username("other").role(Role.USER).build();
        var existing = reaction("allchat:pepe", user, other);
        when(messages.findById(message.getId())).thenReturn(Optional.of(message));
        when(security.getCurrentUser()).thenReturn(user);
        when(reactions.findByMessageIdAndEmoji(message.getId(), existing.getEmoji())).thenReturn(Optional.of(existing));
        when(mapper.toDetailsDTO(existing)).thenReturn(new ReactionDetailsDTO(existing.getId(), message.getId(),
                existing.getEmoji(), existing.getEmojiId(), 2,
                Set.of(new UserMinimalDTO(1L, "member", false, 0), new UserMinimalDTO(2L, "other", false, 0))));
        var result = service.findByMessageIdAndEmoji(message.getId(), existing.getEmoji(), limit);
        assertThat(result.usersCount()).isEqualTo(2);
        assertThat(result.users()).hasSize(Math.min(limit, 2));
        assertThat(existing.getUsers()).containsExactlyInAnyOrder(user, other);
        verifyNoInteractions(users, activity, sockets);
        verify(reactions, never()).save(any());
    }

    @Test
    void formerStaffPrivateMemberCanRemoveButCannotAddAReaction() {
        lockMessage();
        room.setType(ChatRoomType.PRIVATE);
        var existing = reaction("allchat:pepe", user);
        when(reactions.findByMessageIdAndEmoji(message.getId(), existing.getEmoji())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.addReaction(user, message.getId(), "allchat:pepe", "allchat:pepe"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)).hasMessageContaining("staff");
        service.removeReaction(user, message.getId(), existing.getEmoji(), existing.getEmojiId());
        verify(reactions).delete(existing);
        verify(activity).decrementReactionCount(room.getId(), message.getId());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void privateBlockedConversationCannotAddReactionInEitherDirection(boolean counterpartBlocks) {
        lockMessage();
        room.setType(ChatRoomType.PRIVATE);
        user.setRole(Role.MODERATOR);
        User counterpart = User.builder().id(2L).username("other").role(Role.MODERATOR).build();
        if (counterpartBlocks) {
            counterpart.setBlockedUsers(List.of(User.builder().id(user.getId()).build()));
        } else {
            user.setBlockedUsers(List.of(User.builder().id(counterpart.getId()).build()));
        }
        when(privateChats.getCounterpart(user, room)).thenReturn(counterpart);
        assertThatThrownBy(() -> service.addReaction(user, message.getId(), "allchat:pepe", "allchat:pepe"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)).hasMessageContaining("blocked");
        verifyNoInteractions(users, reactions, activity, sockets);
    }

    @Test
    void negativeDetailLimitIsRejected() {
        assertThatThrownBy(() -> service.findByMessageIdAndEmoji(message.getId(), "👍", -1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
