package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.AuditLogActorType;
import com.mk3.chatapp.enums.AuditLogType;

public record RevokeBanAuditLogDTO(
        Long id,
        String createdAt,
        UserDTO createdBy,
        AuditLogActorType createdByType,
        String action,
        String description,
        AuditLogType auditLogType,
        UserDTO targetUser
) implements AuditLogDTO {
}
