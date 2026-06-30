package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.AdminConversationDTO;
import com.mk3.chatapp.dtos.responses.MessagePageDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ChatRoomRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.ChattingService;
import com.mk3.chatapp.services.MessagesService;
import com.mk3.chatapp.services.PrivateChatService;
import com.mk3.chatapp.services.SecurityService;
import com.mk3.chatapp.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConversationServiceImplTest {

    @Mock private SecurityService securityService;
    @Mock private UserService userService;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private UserChatRoomRepository userChatRoomRepository;
    @Mock private MessagesService messagesService;
    @Mock private MessageMapper messageMapper;
    @Mock private UserMapper userMapper;
    @Mock private ChattingService chattingService;
    @Mock private PrivateChatService privateChatService;

    @InjectMocks private AdminConversationServiceImpl service;

    private User admin;
    private User moderator;
    private User superAdmin;
    private User regularUser;

    @BeforeEach
    void setUp() {
        admin = user(1L, Role.ADMIN);
        moderator = user(2L, Role.MODERATOR);
        superAdmin = user(3L, Role.SUPER_ADMIN);
        regularUser = user(4L, Role.USER);
    }

    private User user(Long id, Role role) {
        // @Builder skips the field initializer for blockedUsers; persisted entities always have it.
        return User.builder().id(id).role(role).username("user" + id)
                .blockedUsers(new ArrayList<>()).build();
    }

    private ChatRoom privateRoom(Long id) {
        return ChatRoom.builder().id(id).type(ChatRoomType.PRIVATE).build();
    }

    // ---- listConversations: the privilege filter passed to the query ----

    @Test
    void listConversations_admin_excludesAdminAndSuperAdminCounterpartRoles() {
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(userChatRoomRepository.findCounterpartConversationsForUser(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listConversations(moderator.getId(), null, 0, 20);

        ArgumentCaptor<Set<Role>> rolesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(userChatRoomRepository).findCounterpartConversationsForUser(
                eq(moderator.getId()), rolesCaptor.capture(), eq(""), any(Pageable.class));

        Set<Role> allowed = rolesCaptor.getValue();
        // ADMIN (level 2) strictly outranks MODERATOR/USER/UNCLAIMED_USER/GUEST only.
        assertThat(allowed).contains(Role.MODERATOR, Role.USER, Role.UNCLAIMED_USER, Role.GUEST);
        assertThat(allowed).doesNotContain(Role.ADMIN, Role.SUPER_ADMIN);
    }

    @Test
    void listConversations_superAdmin_includesAdminCounterpartRole() {
        when(securityService.getCurrentUser()).thenReturn(superAdmin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(userChatRoomRepository.findCounterpartConversationsForUser(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listConversations(moderator.getId(), "  ", 0, 20);

        ArgumentCaptor<Set<Role>> rolesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(userChatRoomRepository).findCounterpartConversationsForUser(
                eq(moderator.getId()), rolesCaptor.capture(), eq(""), any(Pageable.class));
        // SUPER_ADMIN (level 3) outranks ADMIN; blank search normalized to empty string.
        assertThat(rolesCaptor.getValue()).contains(Role.ADMIN, Role.MODERATOR, Role.USER);
        assertThat(rolesCaptor.getValue()).doesNotContain(Role.SUPER_ADMIN);
    }

    @Test
    void listConversations_viewerOutranksNobody_returnsEmptyWithoutQuerying() {
        when(securityService.getCurrentUser()).thenReturn(regularUser);
        when(userService.findById(regularUser.getId())).thenReturn(regularUser);

        Page<AdminConversationDTO> result = service.listConversations(regularUser.getId(), null, 0, 20);

        assertThat(result.getContent()).isEmpty();
        verify(userChatRoomRepository, never())
                .findCounterpartConversationsForUser(any(), any(), any(), any());
    }

    @Test
    void listConversations_mapsRowsToDtoWithBothParticipants() {
        ChatRoom room = privateRoom(50L);
        UserChatRoom counterpartRow = UserChatRoom.builder().chatRoom(room).user(regularUser).build();

        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(userChatRoomRepository.findCounterpartConversationsForUser(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(counterpartRow)));
        when(messagesService.getLastMessage(eq(50L), eq(Role.ADMIN))).thenReturn(null);
        when(messagesService.getMessageCount(eq(50L), eq(Role.ADMIN))).thenReturn(7L);
        when(userMapper.toMinimalDto(any(User.class))).thenReturn(null);

        Page<AdminConversationDTO> result = service.listConversations(moderator.getId(), "user", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        AdminConversationDTO dto = result.getContent().get(0);
        assertThat(dto.roomId()).isEqualTo(50L);
        assertThat(dto.totalMessageCount()).isEqualTo(7L);
        assertThat(dto.lastMessage()).isNull();
    }

    // ---- getConversationMessages: visibility enforcement ----

    @Test
    void getConversationMessages_adminReviewingMod_hiddenWhenCounterpartIsAdmin() {
        ChatRoom room = privateRoom(60L);
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(chatRoomRepository.findById(60L)).thenReturn(Optional.of(room));
        when(userChatRoomRepository.findUserChatRoomByUserAndChatRoom(moderator, room))
                .thenReturn(Optional.of(UserChatRoom.builder().chatRoom(room).user(moderator).build()));
        when(privateChatService.getCounterpart(moderator, room)).thenReturn(admin); // counterpart = another ADMIN

        assertThatThrownBy(() ->
                service.getConversationMessages(moderator.getId(), 60L, null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(messagesService, never()).getPaginatedMessages(anyLong(), any(), any(), any(), any());
    }

    @Test
    void getConversationMessages_adminReviewingMod_visibleWhenCounterpartIsUser() {
        ChatRoom room = privateRoom(61L);
        MessagePageDTO page = new MessagePageDTO(List.of(), false, false, null, null);
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(chatRoomRepository.findById(61L)).thenReturn(Optional.of(room));
        when(userChatRoomRepository.findUserChatRoomByUserAndChatRoom(moderator, room))
                .thenReturn(Optional.of(UserChatRoom.builder().chatRoom(room).user(moderator).build()));
        when(privateChatService.getCounterpart(moderator, room)).thenReturn(regularUser);
        when(messagesService.getPaginatedMessages(61L, null, null, null, Role.ADMIN)).thenReturn(page);

        MessagePageDTO result = service.getConversationMessages(moderator.getId(), 61L, null, null, null);

        assertThat(result).isSameAs(page);
        // staff viewer role passed through → deleted messages included
        verify(messagesService).getPaginatedMessages(61L, null, null, null, Role.ADMIN);
    }

    @Test
    void getConversationMessages_targetNotMember_denied() {
        ChatRoom room = privateRoom(62L);
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(chatRoomRepository.findById(62L)).thenReturn(Optional.of(room));
        when(userChatRoomRepository.findUserChatRoomByUserAndChatRoom(moderator, room))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getConversationMessages(moderator.getId(), 62L, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getConversationMessages_nonPrivateRoom_rejected() {
        ChatRoom publicRoom = ChatRoom.builder().id(63L).type(ChatRoomType.PUBLIC).build();
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(chatRoomRepository.findById(63L)).thenReturn(Optional.of(publicRoom));

        assertThatThrownBy(() ->
                service.getConversationMessages(moderator.getId(), 63L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- deleteConversationMessage ----

    @Test
    void deleteConversationMessage_visibleRoom_delegatesToChattingService() {
        ChatRoom room = privateRoom(70L);
        Message message = Message.builder().id(99L).chatRoom(room).sender(regularUser).build();
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(chatRoomRepository.findById(70L)).thenReturn(Optional.of(room));
        when(userChatRoomRepository.findUserChatRoomByUserAndChatRoom(moderator, room))
                .thenReturn(Optional.of(UserChatRoom.builder().chatRoom(room).user(moderator).build()));
        when(privateChatService.getCounterpart(moderator, room)).thenReturn(regularUser);
        when(messagesService.findById(99L, Role.ADMIN)).thenReturn(message);

        service.deleteConversationMessage(moderator.getId(), 70L, 99L);

        verify(chattingService).deleteMessage(99L);
    }

    @Test
    void deleteConversationMessage_hiddenRoom_deniedAndNotDeleted() {
        ChatRoom room = privateRoom(71L);
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(chatRoomRepository.findById(71L)).thenReturn(Optional.of(room));
        when(userChatRoomRepository.findUserChatRoomByUserAndChatRoom(moderator, room))
                .thenReturn(Optional.of(UserChatRoom.builder().chatRoom(room).user(moderator).build()));
        when(privateChatService.getCounterpart(moderator, room)).thenReturn(superAdmin); // outranks admin

        assertThatThrownBy(() ->
                service.deleteConversationMessage(moderator.getId(), 71L, 99L))
                .isInstanceOf(AccessDeniedException.class);

        verify(chattingService, never()).deleteMessage(anyLong());
    }

    @Test
    void deleteConversationMessage_messageInDifferentRoom_denied() {
        ChatRoom room = privateRoom(72L);
        ChatRoom otherRoom = privateRoom(999L);
        Message foreignMessage = Message.builder().id(99L).chatRoom(otherRoom).sender(regularUser).build();
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(userService.findById(moderator.getId())).thenReturn(moderator);
        when(chatRoomRepository.findById(72L)).thenReturn(Optional.of(room));
        when(userChatRoomRepository.findUserChatRoomByUserAndChatRoom(moderator, room))
                .thenReturn(Optional.of(UserChatRoom.builder().chatRoom(room).user(moderator).build()));
        when(privateChatService.getCounterpart(moderator, room)).thenReturn(regularUser);
        when(messagesService.findById(99L, Role.ADMIN)).thenReturn(foreignMessage);

        assertThatThrownBy(() ->
                service.deleteConversationMessage(moderator.getId(), 72L, 99L))
                .isInstanceOf(AccessDeniedException.class);

        verify(chattingService, never()).deleteMessage(anyLong());
    }
}
