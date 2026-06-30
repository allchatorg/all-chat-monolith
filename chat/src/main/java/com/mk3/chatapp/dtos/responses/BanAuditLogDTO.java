package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.AuditLogActorType;
import com.mk3.chatapp.enums.AuditLogType;
import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;

public record BanAuditLogDTO(
        Long id,
        String createdAt,
        UserDTO createdBy,
        AuditLogActorType createdByType,
        String action,
        String description,
        AuditLogType auditLogType,
        UserDTO targetUser,
        BanType banType,
        ReportType reportType,
        Long banDurationSeconds,
        Boolean deleteMessages,
        Long deleteMessagesDurationSeconds
) implements AuditLogDTO {
}
