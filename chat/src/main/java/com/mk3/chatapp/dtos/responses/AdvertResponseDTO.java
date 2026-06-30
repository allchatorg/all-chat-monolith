package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.dtos.AttachmentDTO;

import java.util.List;

public record AdvertResponseDTO(
        Long id,
        String createdAt,
        String content,
        Long senderId,
        String senderUsername,
        String color,
        List<AttachmentDTO> attachments,
        boolean advert
) {
}
