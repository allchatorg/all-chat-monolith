package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Report;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.repositories.ReportCaseRepository;
import com.mk3.chatapp.services.ReportCaseService;
import com.mk3.chatapp.services.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportCaseServiceImpl implements ReportCaseService {
    private final ReportCaseRepository reportCaseRepository;
    private final SecurityService securityService;

    @Override
    public ReportCase createReportForMessage(Message message) {
        var reportCase = ReportCase.builder()
                .message(message)
                .build();
        return reportCaseRepository.save(reportCase);
    }

    @Override
    public boolean existsByMessageId(Long messageId) {
        return reportCaseRepository.existsByMessageId(messageId);
    }

    @Override
    public Optional<ReportCase> getByMessageId(Long messageId) {
        return reportCaseRepository.findByMessageId(messageId);
    }

    @Override
    public ReportCase addReportToCase(Report report) {
        var reportCase = report.getReportCase();
        reportCase.getReports().add(report);
        return reportCaseRepository.save(reportCase);
    }

    @Override
    public Page<ReportCase> findAll(Specification<ReportCase> specification, PageRequest pageRequest) {
        return reportCaseRepository.findAll(specification, pageRequest);
    }

    @Override
    public ReportCase findById(Long reportCaseId) {
        return reportCaseRepository.findById(reportCaseId).orElseThrow(
                () -> new IllegalArgumentException("Report case with id " + reportCaseId + " not found")
        );
    }

    @Override
    public ReportCase save(ReportCase reportCase) {
        return reportCaseRepository.save(reportCase);
    }

    @Override
    public void requestElevation(Long reportCaseId) {
        var reportCase = findById(reportCaseId);
        reportCase.setNeedsAttentionAt(Instant.now());
        reportCaseRepository.save(reportCase);
    }

    @Override
    public void resolveReportCase(Long reportCaseId) {
        var reportCase = findById(reportCaseId);
        var user = securityService.getCurrentUser();
        reportCase.setResolutionDate(Instant.now());
        reportCase.setResolver(user);

        reportCaseRepository.save(reportCase);
    }
}
