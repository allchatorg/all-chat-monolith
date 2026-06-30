package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.AttachmentTypeEnum;

public record MediaProcessorBankAttachmentRequestDTO(
        Long attachmentId,
        String filename,
        String mimeType,
        AttachmentTypeEnum attachmentType,
        String bankName) {
}
