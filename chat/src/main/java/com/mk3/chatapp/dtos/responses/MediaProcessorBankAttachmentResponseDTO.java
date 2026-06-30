package com.mk3.chatapp.dtos.responses;

import java.util.Map;

public record MediaProcessorBankAttachmentResponseDTO(
        String bankName,
        Long bankContentId,
        Map<String, String> signals) {
}
