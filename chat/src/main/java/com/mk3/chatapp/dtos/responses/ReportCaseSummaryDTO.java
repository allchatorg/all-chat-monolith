package com.mk3.chatapp.dtos.responses;


public record ReportCaseSummaryDTO(
        Long id,
        MessageResponseDTO message,
        int reportCount,
        UserDTO resolver,
        String needsAttentionAt,
        String resolutionDate,
        boolean csamCase
) {
}