package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.dtos.AttachmentDTO;

import java.util.List;

public record CreateMessageRequestDTO(
        String content,
        Long chatRoomId,
        List<AttachmentDTO> attachments,
        Long replyToMessageId
) {
}
