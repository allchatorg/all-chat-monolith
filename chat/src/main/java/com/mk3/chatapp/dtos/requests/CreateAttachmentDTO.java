package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.AttachmentTypeEnum;

public record CreateAttachmentDTO(
        AttachmentTypeEnum type,
        String url
) {
}
