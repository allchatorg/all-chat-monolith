package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.AuditLogActorType;
import com.mk3.chatapp.enums.AuditLogType;

public interface AuditLogDTO {
    Long id();

    String createdAt();

    UserDTO createdBy();

    AuditLogActorType createdByType();

    UserDTO targetUser();

    String action();

    String description();

    AuditLogType auditLogType();
}
