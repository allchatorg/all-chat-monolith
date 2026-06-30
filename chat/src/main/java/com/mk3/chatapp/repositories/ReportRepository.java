package com.mk3.chatapp.repositories;

import com.mk3.chatapp.enums.ReportOrigin;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Report;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByReportCaseId(Long reportCaseId);

    List<Report> findAllByReportCase(ReportCase reportCase);

    long countByReportCaseId(Long reportCaseId);

    long countByMessageId(Long messageId);

    boolean existsByReporterAndMessageAndReportType(User reporter, Message message, ReportType reportType);

    boolean existsByMessageAndReportTypeAndReporterOrigin(Message message, ReportType reportType,
                                                          ReportOrigin reporterOrigin);
}
