package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.AuditLogActorType;
import com.mk3.chatapp.enums.AuditLogType;

public record BanAppealAuditLogDTO(
        Long id,
        String createdAt,
        UserDTO createdBy,
        AuditLogActorType createdByType,
        String action,
        String description,
        AuditLogType auditLogType,
        UserDTO targetUser,
        Long appealId,
        Long banId,
        String decision
) implements AuditLogDTO {
}
