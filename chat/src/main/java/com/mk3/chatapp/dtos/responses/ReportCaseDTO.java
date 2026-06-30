package com.mk3.chatapp.dtos.responses;

import java.util.List;

public record ReportCaseDTO(
        Long id,
        MessageResponseDTO message,
        List<ReportDTO> reports,
        List<AuditLogDTO> auditLogs,
        UserDTO resolver,
        String needsAttentionAt,
        String resolutionDate,
        boolean csamCase) {
}