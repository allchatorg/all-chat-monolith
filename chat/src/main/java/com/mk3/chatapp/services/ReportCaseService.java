package com.mk3.chatapp.services;

import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Report;
import com.mk3.chatapp.models.ReportCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public interface ReportCaseService {
    ReportCase createReportForMessage(Message message);

    boolean existsByMessageId(Long messageId);

    Optional<ReportCase> getByMessageId(Long messageId);

    ReportCase addReportToCase(Report report);

    Page<ReportCase> findAll(Specification<ReportCase> specification, PageRequest pageRequest);

    ReportCase findById(Long reportCaseId);

    ReportCase save(ReportCase reportCase);

    void requestElevation(Long reportCaseId);

    void resolveReportCase(Long reportCaseId);
}
