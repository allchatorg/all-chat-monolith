package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.WarnUserRequestDTO;
import com.mk3.chatapp.dtos.requests.BanRequestDTO;
import com.mk3.chatapp.dtos.requests.ReportSearchRequestDTO;
import com.mk3.chatapp.dtos.responses.ReportCaseDTO;
import com.mk3.chatapp.dtos.responses.ReportCaseSummaryDTO;
import com.mk3.chatapp.enums.NcmecFileAnnotation;
import com.mk3.chatapp.enums.NcmecIncidentType;
import com.mk3.chatapp.enums.NcmecReportAnnotation;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.models.AuditLog;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ReportManagerService {
    ReportCaseDTO submitNcmecReport(Long reportCaseId, NcmecIncidentType incidentType,
                                    List<NcmecReportAnnotation> reportAnnotations, List<NcmecFileAnnotation> fileAnnotations,
                                    User initiatingUser);

    void createReportForMessage(Long messageId, ReportType reportType, String description);

    void createSystemReportForMessage(Long messageId, ReportType reportType, String description);

    Page<ReportCaseSummaryDTO> searchReportCases(ReportSearchRequestDTO request);

    ReportCaseDTO getReportCaseDTO(Long reportCaseId);

    ReportCase getReportCase(Long reportCaseId);

    void addLogToReportCase(Long reportCaseId, AuditLog log);

    void warnUser(WarnUserRequestDTO warnRequestDTO, Long reportCaseId);

    void deleteMessage(Long reportCaseId, Long messageId);

    void requestElevation(Long reportCaseId);

    void banUser(Long reportCaseId, BanRequestDTO banRequestDTO);

    ReportCaseDTO resolveCase(Long reportCaseId);

    ReportCaseDTO resolveCsamCase(Long reportCaseId);
}
