package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.AuditLogDTO;
import com.mk3.chatapp.models.AuditLog;
import org.springframework.stereotype.Service;

@Service
public interface AuditLogCustomMapper {
    AuditLogDTO toAuditLogDTO(AuditLog auditLog);
}
