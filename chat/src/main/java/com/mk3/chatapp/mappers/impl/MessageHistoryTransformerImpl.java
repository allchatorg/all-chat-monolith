package com.mk3.chatapp.mappers.impl;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.mappers.AttachmentMapper;
import com.mk3.chatapp.mappers.MessageHistoryTransformer;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.MessageEditHistory;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.utils.DateTimeMapperUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MessageHistoryTransformerImpl implements MessageHistoryTransformer {

    private final AttachmentMapper attachmentMapper;

    @Override
    public MessageResponseDTO transform(MessageEditHistory history) {
        if (history == null) return null;

        Message message = history.getMessage();
        ChatRoom chatRoom = message != null ? message.getChatRoom() : null;
        User sender = message != null ? message.getSender() : null;

        List<AttachmentDTO> attachments = history.getAttachments() == null ? List.of()
                : history.getAttachments().stream().filter(Objects::nonNull).map(attachmentMapper::toDto).toList();

        Long id = message != null ? message.getId() : null;
        Long chatRoomId = chatRoom != null ? chatRoom.getId() : null;
        String chatRoomName = chatRoom != null ? chatRoom.getName() : null;
        Long senderId = sender != null ? sender.getId() : null;
        String senderUsername = sender != null ? sender.getApplicationUsername() : null;
        var senderRole = sender != null ? sender.getRole() : null;
        boolean bannedUser = sender != null && sender.isBanned();
        boolean deleted = message != null && Boolean.TRUE.equals(message.getDeleted());
        String createdAt = message != null ? DateTimeMapperUtil.instantToString(message.getCreatedAt()) : null;
        String editedAt = DateTimeMapperUtil.instantToString(history.getCreatedAt());
        String color = sender != null ? sender.getDisplayColor() : null;


        // Reactions are intentionally not mapped for history response per requirement
        return new MessageResponseDTO(
                id,
                history.getContent(),
                chatRoomId,
                chatRoomName,
                senderId,
                senderUsername,
                senderRole,
                null,
                bannedUser,
                deleted,
                createdAt,
                editedAt,
                color,
                attachments,
                List.of(),
                null,
                null
        );
    }

    @Override
    public List<MessageResponseDTO> transform(List<MessageEditHistory> histories) {
        if (histories == null) return List.of();
        return histories.stream().map(this::transform).toList();
    }
}
