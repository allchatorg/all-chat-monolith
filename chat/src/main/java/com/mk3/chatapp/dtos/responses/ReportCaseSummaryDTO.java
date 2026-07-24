package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.ReportType;

import java.util.List;

public record ReportCaseSummaryDTO(
        Long id,
        MessageResponseDTO message,
        int reportCount,
        List<ReportType> reportTypes,
        UserDTO resolver,
        String needsAttentionAt,
        String resolutionDate,
        boolean csamCase
) {
}