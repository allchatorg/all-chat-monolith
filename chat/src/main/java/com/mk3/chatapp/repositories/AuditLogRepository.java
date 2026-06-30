package com.mk3.chatapp.repositories;

import com.mk3.chatapp.enums.AuditLogType;
import com.mk3.chatapp.models.AuditLog;
import com.mk3.chatapp.models.MessageDeleteAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    Page<AuditLog> findByLogType(AuditLogType logType, Pageable pageable);

    Page<AuditLog> findByCreatedBy(String createdBy, Pageable pageable);

    @Query("SELECT m FROM MessageDeleteAuditLog m WHERE m.deletedMessageId = :deletedMessageId")
    Optional<MessageDeleteAuditLog> findByDeletedMessageId(@Param("deletedMessageId") String deletedMessageId);
}
