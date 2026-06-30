package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.AuditLogActorType;
import com.mk3.chatapp.enums.AuditLogType;

public record NcmecReportAuditLogDTO(
        Long id,
        String createdAt,
        UserDTO createdBy,
        AuditLogActorType createdByType,
        String action,
        String description,
        AuditLogType auditLogType,
        UserDTO targetUser,
        Long ncmecReportId,
        Long reportCaseId,
        String xmlContent,
        String status) implements AuditLogDTO {
}
