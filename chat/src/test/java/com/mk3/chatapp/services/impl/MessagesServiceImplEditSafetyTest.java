package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.mappers.MessageEditHistoryMapper;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagesServiceImplEditSafetyTest {
    @Mock private MessageRepository messageRepository;
    @Mock private WebSocketBroadcastService webSocketBroadcastService;
    @Mock private MessageMapper messageMapper;
    @Mock private RoomActivityService roomActivityService;
    @Mock private MessageEditHistoryService messageEditHistoryService;
    @Mock private MessageEditHistoryMapper messageEditHistoryMapper;
    @Mock private AttachmentService attachmentService;
    @Mock private ChatRoomService chatRoomService;
    @Mock private UserChatRoomRepository userChatRoomRepository;
    @Mock private MessagePromotionEnrichmentService messagePromotionEnrichmentService;
    @InjectMocks private MessagesServiceImpl service;

    private User sender;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        sender = User.builder().id(1L).role(Role.USER).username("sender").showProBadge(false).build();
        room = ChatRoom.builder().id(5L).name("room").type(ChatRoomType.PUBLIC).build();
    }

    @Test
    void unrelatedAttachmentCannotBeRemovedFromMessage() {
        var message = editableMessage();
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.removeAttachmentFromMessage(message.getId(), 10L, sender))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong");
        verifyNoInteractions(attachmentService);
    }

    @Test
    void removingLastAttachmentWithoutTextFailsBeforeDeletingIt() {
        var message = editableMessage();
        message.setContent("");
        var attachment = new Attachment();
        attachment.setId(10L);
        message.setAttachments(new ArrayList<>(List.of(attachment)));
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.removeAttachmentFromMessage(message.getId(), 10L, sender))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be left empty");
        verifyNoInteractions(attachmentService);
    }

    @Test
    void deletedMessagesCannotBeEdited() {
        var message = editableMessage();
        message.setDeleted(true);
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.editMessage(message.getId(), "new caption", sender))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Removed");
        verify(messageRepository, never()).save(any());
    }

    private Message editableMessage() {
        return Message.builder().id(11L).chatRoom(room).sender(sender).content("caption")
                .attachments(new ArrayList<>()).build();
    }

}
