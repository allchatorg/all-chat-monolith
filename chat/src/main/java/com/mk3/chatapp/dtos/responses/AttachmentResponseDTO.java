package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.AttachmentTypeEnum;

public record AttachmentResponseDTO(
        Long id,
        AttachmentTypeEnum type,
        String url
) {
}
