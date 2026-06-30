package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.AuditLogActorType;
import com.mk3.chatapp.enums.AuditLogType;

public record AuditLogSearchRequestDTO(
        Long userId,
        Long createdByUserId,
        AuditLogActorType createdByType,
        Long targetUserId,
        AuditLogType auditLogType,
        String sort,
        int page,
        int size
) {
}
