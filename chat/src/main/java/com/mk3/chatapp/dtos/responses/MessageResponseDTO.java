package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.enums.Role;

import java.util.List;

public record MessageResponseDTO(
        Long id,
        String content,
        Long chatRoomId,
        String chatRoomName,
        Long senderId,
        String senderUsername,
        Role senderRole,
        String senderCountryCode,
        boolean bannedUser,
        boolean deleted,
        String createdAt,
        String editedAt,
        String color,
        List<AttachmentDTO> attachments,
        List<ReactionSummaryDTO> reactions,
        ReplyInfoDTO replyTo
) {
    public MessageResponseDTO withReplyTo(ReplyInfoDTO replyTo) {
        return new MessageResponseDTO(id, content, chatRoomId, chatRoomName, senderId, senderUsername,
                senderRole, senderCountryCode, bannedUser, deleted, createdAt, editedAt, color,
                attachments, reactions, replyTo);
    }
}
