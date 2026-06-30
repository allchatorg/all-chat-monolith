package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.ncmec.NcmecReportAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NcmecReportAuditLogRepository extends JpaRepository<NcmecReportAuditLog, Long> {
    Optional<NcmecReportAuditLog> findByNcmecReportId(Long ncmecReportId);
}
