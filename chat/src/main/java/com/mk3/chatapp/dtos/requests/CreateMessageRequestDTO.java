package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.dtos.AttachmentDTO;

import java.util.List;

public record CreateMessageRequestDTO(
        String content,
        Long chatRoomId,
        List<AttachmentDTO> attachments,
        Long replyToMessageId,
        String stickerId
) {
    public CreateMessageRequestDTO(String content, Long chatRoomId, List<AttachmentDTO> attachments,
                                   Long replyToMessageId) {
        this(content, chatRoomId, attachments, replyToMessageId, null);
    }
}
