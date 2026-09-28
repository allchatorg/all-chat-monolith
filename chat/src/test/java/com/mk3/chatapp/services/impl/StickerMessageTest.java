package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.requests.CreateMessageRequestDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.mappers.ReactionMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StickerMessageTest {
    @Mock private MessageRepository messages;
    @Mock private UserRepository users;
    @Mock private ChatRoomService rooms;
    @Mock private UserChatRoomRepository members;
    @Mock private WebSocketBroadcastService sockets;
    @Mock private MessageEditHistoryService history;
    private final MessageMapper mapper = Mappers.getMapper(MessageMapper.class);
    private MessagesServiceImpl service;
    private User user;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mapper, "reactionMapper", Mappers.getMapper(ReactionMapper.class));
        user = User.builder().id(1L).role(Role.USER).username("member").showProBadge(false).build();
        room = ChatRoom.builder().id(5L).name("room").type(ChatRoomType.PUBLIC).build();
        service = new MessagesServiceImpl(messages, sockets, mapper, null, history, null, null, rooms, members,
                null, new ProStickerService(new ProBadgeService(users, null, null, mock(ProFontService.class))));
    }

    private CreateMessageRequestDTO request(String stickerId) {
        return new CreateMessageRequestDTO("", room.getId(), List.of(), null, stickerId);
    }

    private void activePro() {
        when(users.findEligibleProPaidThrough(user.getId())).thenReturn(Optional.of(Instant.now().plusSeconds(3600)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wojak", "soyjak", "chud", "chad-1", "chad-2", "virgin", "doomer", "coomer",
            "bloomer", "zoomer", "npc", "grug", "pepe", "apu-apustaja", "honkler", "spurdo", "gondola"})
    void allCatalogStickersSaveWithFreshEntitlementAndHiddenBadge(String id) {
        activePro();
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Message message = service.saveMessage(request(id), user, room);

        assertThat(message.getStickerId()).isEqualTo(id);
        assertThat(message.getContent()).isEmpty();
        assertThat(user.isProBadgeVisible()).isFalse();
        assertThat(mapper.toMessageResponseDTO(message).stickerId()).isEqualTo(id);
        verify(users).findEligibleProPaidThrough(user.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void freeAndExpiredMembersCannotSendDespiteCachedEntitlement(boolean expired) {
        user.setProPaidThrough(Instant.now().plusSeconds(3600));
        when(users.findEligibleProPaidThrough(user.getId())).thenReturn(expired
                ? Optional.of(Instant.now().minusSeconds(1)) : Optional.empty());

        assertThatThrownBy(() -> service.saveMessage(request("pepe"), user, room))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(messages, sockets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PEPE", "unknown", "allchat:pepe", "../pepe", "/stickers/pro/pepe.png",
            "https://example.com/pepe.png"})
    void onlyExactCatalogIdsAreAccepted(String id) {
        assertThatThrownBy(() -> service.saveMessage(request(id), user, room))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(users, messages, sockets);
    }

    @Test
    void stickerCannotBeCombinedWithTextOrAttachments() {
        var attachment = new AttachmentDTO(8L, null, "image", null, null, null, null, null);
        assertThatThrownBy(() -> service.saveMessage(
                new CreateMessageRequestDTO("caption", room.getId(), List.of(), null, "pepe"), user, room))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("text or attachments");
        assertThatThrownBy(() -> service.saveMessage(
                new CreateMessageRequestDTO("", room.getId(), List.of(attachment), null, "pepe"), user, room))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("text or attachments");
        verifyNoInteractions(users, messages, sockets);
    }

    @Test
    void emptyOrdinaryMessagesStillFailAndOrdinaryMessagesDoNotCheckPro() {
        assertThatThrownBy(() -> service.saveMessage(request(null), user, room))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty");
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.saveMessage(new CreateMessageRequestDTO("hello", room.getId(), null, null), user, room)
                .getStickerId()).isNull();
        verifyNoInteractions(users);
    }

    @Test
    void stickerCanReplyWithinRoomAndCannotReplyAcrossRooms() {
        activePro();
        var parent = Message.builder().id(8L).chatRoom(room).sender(user).content("hello").build();
        when(messages.findById(8L)).thenReturn(Optional.of(parent));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateMessageRequestDTO(null, room.getId(), null, 8L, "pepe");
        assertThat(service.saveMessage(request, user, room).getReplyTo()).isSameAs(parent);
        parent.setChatRoom(ChatRoom.builder().id(6L).build());
        assertThatThrownBy(() -> service.saveMessage(request, user, room))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("another chat room");
    }

    @ParameterizedTest
    @EnumSource(value = ChatRoomType.class, names = {"PUBLIC", "PRIVATE"})
    void standaloneStickerUsesExistingPublicAndPrivateBroadcasts(ChatRoomType type) {
        room.setType(type);
        activePro();
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        if (type == ChatRoomType.PRIVATE) {
            when(rooms.findById(room.getId())).thenReturn(room);
            when(members.findByChatRoom(room)).thenReturn(List.of(UserChatRoom.builder().user(user).build()));
        }
        Message message = service.saveMessage(request("pepe"), user, room);
        var response = service.broadcastMessage(message, null);
        var payload = ArgumentCaptor.forClass(WebSocketMessage.class);
        if (type == ChatRoomType.PRIVATE) {
            verify(sockets).sendPrivateToUser(eq(user.getId()), payload.capture());
            assertThat(payload.getValue().getType()).isEqualTo(WebSocketMessageType.PRIVATE_NEW_MESSAGE);
            verify(sockets, never()).broadcastToChatRoom(anyString(), any());
        } else {
            verify(sockets).broadcastToChatRoom(eq(room.getName()), payload.capture());
            assertThat(payload.getValue().getType()).isEqualTo(WebSocketMessageType.NEW_MESSAGE);
            verify(sockets, never()).sendPrivateToUser(anyLong(), any());
        }
        assertThat(response.stickerId()).isEqualTo("pepe");
        assertThat(payload.getValue().getData()).isEqualTo(response);
    }

    @Test
    void immutableStickerCannotBecomeTextOrCreateEditHistory() {
        var message = Message.builder().id(8L).chatRoom(room).sender(user).content("").stickerId("pepe").build();
        when(messages.findById(8L)).thenReturn(Optional.of(message));
        assertThatThrownBy(() -> service.editMessage(8L, "replacement", user))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Sticker messages cannot be edited");
        verify(messages, never()).save(any());
        verifyNoInteractions(history);
    }
}
