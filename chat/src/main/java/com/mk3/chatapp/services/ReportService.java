package com.mk3.chatapp.services;

import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Report;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.models.identity.User;
import org.springframework.stereotype.Service;

@Service
public interface ReportService {
    Report createReportForMessage(Message message, ReportType reportType, ReportCase reportCase, User reporter,
                                  String description);

    Report createSystemReportForMessage(Message message, ReportType reportType, ReportCase reportCase,
                                        String description);

    boolean existsSystemReportForMessage(Message message, ReportType reportType);
}
