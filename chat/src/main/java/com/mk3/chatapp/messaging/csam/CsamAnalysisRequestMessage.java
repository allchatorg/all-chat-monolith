package com.mk3.chatapp.messaging.csam;

import com.mk3.chatapp.enums.AttachmentTypeEnum;

import java.time.Instant;

public record CsamAnalysisRequestMessage(
        Long attachmentId,
        Long uploaderUserId,
        String originalFilename,
        Long sizeBytes,
        String storageKey,
        String downloadUrl,
        String mimeType,
        AttachmentTypeEnum attachmentType,
        Instant uploadedAt) {
}
