package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.ReportCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ReportCaseRepository extends JpaRepository<ReportCase, Long>, JpaSpecificationExecutor<ReportCase> {
    Optional<ReportCase> findByMessageId(Long messageId);

    boolean existsByMessageId(Long messageId);
}
