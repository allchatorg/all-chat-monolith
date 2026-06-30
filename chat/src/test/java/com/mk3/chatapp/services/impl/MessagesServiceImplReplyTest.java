package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.CreateMessageRequestDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.mappers.MessageEditHistoryMapper;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.AttachmentService;
import com.mk3.chatapp.services.ChatRoomService;
import com.mk3.chatapp.services.MessageEditHistoryService;
import com.mk3.chatapp.services.RoomActivityService;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagesServiceImplReplyTest {

    @Mock private MessageRepository messageRepository;
    @Mock private WebSocketBroadcastService webSocketBroadcastService;
    @Mock private MessageMapper messageMapper;
    @Mock private RoomActivityService roomActivityService;
    @Mock private MessageEditHistoryService messageEditHistoryService;
    @Mock private MessageEditHistoryMapper messageEditHistoryMapper;
    @Mock private AttachmentService attachmentService;
    @Mock private ChatRoomService chatRoomService;
    @Mock private UserChatRoomRepository userChatRoomRepository;

    @InjectMocks private MessagesServiceImpl service;

    private User sender;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        sender = User.builder().id(1L).role(Role.USER).username("sender").build();
        room = ChatRoom.builder().id(5L).build();
    }

    private CreateMessageRequestDTO request(Long replyToMessageId) {
        return new CreateMessageRequestDTO("hello", room.getId(), null, replyToMessageId);
    }

    private Message parentInRoom(ChatRoom chatRoom) {
        return Message.builder()
                .id(99L)
                .content("original")
                .sender(User.builder().id(2L).build())
                .chatRoom(chatRoom)
                .build();
    }

    @Test
    void saveMessage_withoutReply_savesNullReplyTo() {
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveMessage(request(null), sender, room);

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getReplyTo()).isNull();
        verify(messageRepository, never()).findById(any(Long.class));
    }

    @Test
    void saveMessage_withReply_linksParent() {
        var parent = parentInRoom(room);
        when(messageRepository.findById(99L)).thenReturn(Optional.of(parent));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveMessage(request(99L), sender, room);

        var captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getReplyTo()).isSameAs(parent);
    }

    @Test
    void saveMessage_replyToMissingParent_throws() {
        when(messageRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveMessage(request(99L), sender, room))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void saveMessage_replyToParentInDifferentRoom_throws() {
        var parent = parentInRoom(ChatRoom.builder().id(6L).build());
        when(messageRepository.findById(99L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.saveMessage(request(99L), sender, room))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another chat room");
    }

    @Test
    void saveMessage_replyToDeletedParent_throws() {
        var parent = parentInRoom(room);
        parent.setDeleted(true);
        when(messageRepository.findById(99L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.saveMessage(request(99L), sender, room))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("removed message");
    }

    @Test
    void saveMessage_replyToQuarantinedParent_throws() {
        var parent = parentInRoom(room);
        parent.setQuarantined(true);
        when(messageRepository.findById(99L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.saveMessage(request(99L), sender, room))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("removed message");
    }
}
