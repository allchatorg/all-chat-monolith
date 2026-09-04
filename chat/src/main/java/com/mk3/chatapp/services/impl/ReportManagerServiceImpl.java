package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.WarnUserRequestDTO;
import com.mk3.chatapp.dtos.requests.BanRequestDTO;
import com.mk3.chatapp.dtos.requests.ReportSearchRequestDTO;
import com.mk3.chatapp.dtos.responses.ReportCaseDTO;
import com.mk3.chatapp.dtos.responses.ReportCaseSummaryDTO;
import com.mk3.chatapp.dtos.responses.ReportNotificationDTO;
import com.mk3.chatapp.enums.*;
import com.mk3.chatapp.mappers.ReportCaseMapper;
import com.mk3.chatapp.models.AuditLog;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.NcmecReportAuditLogRepository;
import com.mk3.chatapp.services.*;
import com.mk3.chatapp.specifications.ReportCaseSpecification;
import com.mk3.chatapp.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportManagerServiceImpl implements ReportManagerService {
    private static final String CSAM_AUTO_DELETE_DESCRIPTION =
            "Automatically removed after a positive CSAM analysis result.";

    private final NcmecReportingService ncmecReportingService;

    private final ReportService reportService;

    private final ReportCaseService reportCaseService;
    private final ReportCaseMapper reportCaseMapper;

    private final MessagesService messagesService;
    private final SecurityService securityService;
    private final AdminFacadeService adminFacadeService;
    private final ChattingService chattingService;
    private final AuditLogService auditLogService;
    private final UserService userService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final AttachmentService attachmentService;
    private final NcmecReportAuditLogRepository ncmecReportAuditLogRepository;

    @Transactional
    @Override
    public void createReportForMessage(Long messageId, ReportType reportType, String description) {
        var message = messagesService.findById(messageId);
        Optional<ReportCase> reportCase = reportCaseService.getByMessageId(messageId);
        var reporter = securityService.getCurrentUser();

        if (reportCase.isPresent()) {
            reportService.createReportForMessage(message, reportType, reportCase.get(), reporter, description);
            return;
        }

        var newReportCase = reportCaseService.createReportForMessage(message);
        reportService.createReportForMessage(message, reportType, newReportCase, reporter, description);

        broadcastReportToStaff(reporter, newReportCase);
    }

    private void broadcastReportToStaff(User reporter, ReportCase reportCase) {
        broadcastReportToStaffByReporterId(reporter.getId(), reportCase);
    }

    private void broadcastReportToStaffByReporterId(Long reporterId, ReportCase reportCase) {
        var staffMembers = userService.findStaffMembers();
        var reportCaseDTO = reportCaseMapper.toDto(reportCase);

        var reportNotification = new ReportNotificationDTO(
                reporterId,
                reportCase.getId(),
                reportCaseDTO);

        var webSocketMessage = com.mk3.chatapp.models.WebSocketMessage.builder()
                .type(WebSocketMessageType.REPORT_NOTIFICATION)
                .data(reportNotification)
                .build();

        webSocketBroadcastService.broadcastToUsers(staffMembers, webSocketMessage);
    }

    @Transactional
    @Override
    public void createSystemReportForMessage(Long messageId, ReportType reportType, String description) {
        var message = messagesService.findById(messageId);
        Optional<ReportCase> existingReportCase = reportCaseService.getByMessageId(messageId);

        ReportCase reportCase = existingReportCase.orElseGet(() -> reportCaseService.createReportForMessage(message));
        reportCaseService.save(reportCase);

        if (reportService.existsSystemReportForMessage(message, reportType)) {
            chattingService.deleteMessageAsSystem(messageId, CSAM_AUTO_DELETE_DESCRIPTION)
                    .ifPresent(log -> addLogToReportCase(reportCase.getId(), log));
            return;
        }

        reportService.createSystemReportForMessage(message, reportType, reportCase, description);
        broadcastReportToStaffByReporterId(null, reportCase);
        chattingService.deleteMessageAsSystem(messageId, CSAM_AUTO_DELETE_DESCRIPTION)
                .ifPresent(log -> addLogToReportCase(reportCase.getId(), log));
    }

    @Override
    public Page<ReportCaseSummaryDTO> searchReportCases(ReportSearchRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        if (request.page() < 0 || request.size() <= 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        // the reports count is handled in the specification
        // since we don't keep a count in ReportCase but a list of reports
        List<Sort.Order> sortOrders = Utils.jsonStringToSortOrder(request.sort())
                .stream().filter(sortOrder -> !sortOrder.getProperty().equals("reportCount"))
                .toList();

        PageRequest pageRequest = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by(sortOrders));

        Specification<ReportCase> specification = ReportCaseSpecification.getCompleteSpecification(request);

        return reportCaseService.findAll(specification, pageRequest).map(reportCaseMapper::toSummaryDto);

    }

    @Override
    public ReportCaseDTO getReportCaseDTO(Long reportCaseId) {
        ReportCase reportCase = reportCaseService.findById(reportCaseId);
        return reportCaseMapper.toDto(reportCase);
    }

    @Override
    public ReportCase getReportCase(Long reportCaseId) {
        return reportCaseService.findById(reportCaseId);
    }

    @Transactional
    @Override
    public void addLogToReportCase(Long reportCaseId, AuditLog log) {
        ReportCase reportCase = reportCaseService.findById(reportCaseId);
        var logs = reportCase.getAuditLogs();
        if (logs == null) {
            logs = new ArrayList<>();
        }
        logs.add(log);
        reportCase.setAuditLogs(logs);
        reportCaseService.save(reportCase);
    }

    @Override
    public void warnUser(WarnUserRequestDTO warnRequestDTO, Long reportCaseId) {
        AuditLog log = adminFacadeService.warnUser(warnRequestDTO);
        addLogToReportCase(reportCaseId, log);
    }

    @Transactional
    @Override
    public void deleteMessage(Long reportCaseId, Long messageId) {
        var deletedMessage = chattingService.deleteMessage(messageId);
        AuditLog log = auditLogService.findDeletedMessageAuditLog(deletedMessage.getId());

        addLogToReportCase(reportCaseId, log);
    }

    @Override
    public void requestElevation(Long reportCaseId) {
        reportCaseService.requestElevation(reportCaseId);
    }

    @Transactional
    @Override
    public void banUser(Long reportCaseId, BanRequestDTO banRequestDTO) {
        var log = adminFacadeService.banUser(banRequestDTO);
        addLogToReportCase(reportCaseId, log);
        reportCaseService.resolveReportCase(reportCaseId);
    }

    @Transactional
    @Override
    public ReportCaseDTO resolveCase(Long reportCaseId) {
        ReportCase reportCase = reportCaseService.findById(reportCaseId);
        Long targetUserId = reportCase.getMessage() != null && reportCase.getMessage().getSender() != null
                ? reportCase.getMessage().getSender().getId()
                : null;

        reportCaseService.resolveReportCase(reportCaseId);
        var log = auditLogService.logResolveCase(
                "RESOLVE_CASE",
                "Report case resolved",
                targetUserId,
                reportCaseId);
        addLogToReportCase(reportCaseId, log);
        return getReportCaseDTO(reportCaseId);
    }

    @Transactional
    @Override
    public ReportCaseDTO resolveCsamCase(Long reportCaseId) {
        ReportCase reportCase = reportCaseService.findById(reportCaseId);
        reportCase.setCsamCase(true);
        reportCaseService.save(reportCase);
        return resolveCase(reportCaseId);
    }

    @Override
    @Transactional
    public ReportCaseDTO submitNcmecReport(Long reportCaseId,
                                           NcmecIncidentType incidentType,
                                           List<NcmecReportAnnotation> reportAnnotations,
                                           List<NcmecFileAnnotation> fileAnnotations,
                                           User initiatingUser) {
        ReportCase reportCase = getReportCase(reportCaseId);
        Long ncmecReportId = ncmecReportingService.openReport(reportCase, incidentType, reportAnnotations,
                fileAnnotations, initiatingUser);
        User targetUser = reportCase.getMessage().getSender();
        attachmentService.deleteAllByUserId(targetUser.getId().toString());

        // Add the NCMEC audit log to the report case
        ncmecReportAuditLogRepository.findByNcmecReportId(ncmecReportId)
                .ifPresent(ncmecLog -> addLogToReportCase(reportCaseId, ncmecLog));

        BanRequestDTO banRequestDTO = new BanRequestDTO(
                targetUser.getId(),
                null,
                ReportType.REAL_CHILD_SEXUAL_ABUSE_MATERIAL,
                BanType.PERMANENT,
                "User banned due to CSAM.",
                true,
                null,
                reportCaseId);

        // Add the ban audit log to the report case
        AuditLog banLog = adminFacadeService.banUser(banRequestDTO, initiatingUser);
        addLogToReportCase(reportCaseId, banLog);

        userService.quarantineUser(targetUser);

        return resolveCsamCase(reportCaseId);
    }
}
