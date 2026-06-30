package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.dtos.AttachmentDTO;

import java.util.List;

public record MessageEditHistoryDTO(
        Long id,
        Long messageId,
        String content,
        Long chatRoomId,
        Long senderId,
        Long senderUsername,
        String createdAt,
        List<AttachmentDTO> attachments
) {
}
