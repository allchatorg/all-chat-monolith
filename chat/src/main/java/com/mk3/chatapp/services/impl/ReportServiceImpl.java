package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.enums.ReportOrigin;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Report;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ReportRepository;
import com.mk3.chatapp.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    private final ReportRepository reportRepository;

    @Override
    public Report createReportForMessage(Message message, ReportType reportType, ReportCase reportCase, User reporter,
                                         String description) {
        var report = Report.builder()
                .message(message)
                .reportType(reportType)
                .reportCase(reportCase)
                .reporter(reporter)
                .reporterOrigin(ReportOrigin.USER)
                .reportedUser(message.getSender())
                .description(description)
                .build();
        return reportRepository.save(report);
    }

    @Override
    public Report createSystemReportForMessage(Message message, ReportType reportType, ReportCase reportCase,
                                               String description) {
        var report = Report.builder()
                .message(message)
                .reportType(reportType)
                .reportCase(reportCase)
                .reporter(null)
                .reporterOrigin(ReportOrigin.SYSTEM)
                .reportedUser(message.getSender())
                .description(description)
                .build();
        return reportRepository.save(report);
    }

    @Override
    public boolean existsSystemReportForMessage(Message message, ReportType reportType) {
        return reportRepository.existsByMessageAndReportTypeAndReporterOrigin(message, reportType, ReportOrigin.SYSTEM);
    }
}
