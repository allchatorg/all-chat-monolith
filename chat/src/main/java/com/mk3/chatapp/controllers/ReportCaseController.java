package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.WarnUserRequestDTO;
import com.mk3.chatapp.dtos.requests.BanRequestDTO;
import com.mk3.chatapp.dtos.requests.NcmecReportRequestDTO;
import com.mk3.chatapp.dtos.requests.ReportSearchRequestDTO;
import com.mk3.chatapp.dtos.responses.ReportCaseDTO;
import com.mk3.chatapp.dtos.responses.ReportCaseSummaryDTO;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.ReportManagerService;
import com.mk3.chatapp.services.SecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/v1/report-cases")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.FRONT_END.URL}", allowCredentials = "true")
public class ReportCaseController {
    private final ReportManagerService reportManagerService;
    private final SecurityService securityService;

    @GetMapping
    public Page<ReportCaseSummaryDTO> searchReportCases(ReportSearchRequestDTO request) {
        return reportManagerService.searchReportCases(request);
    }

    @GetMapping("/{reportCaseId}")
    public ReportCaseDTO getReportCase(@PathVariable Long reportCaseId) {
        return reportManagerService.getReportCaseDTO(reportCaseId);
    }

    @PatchMapping("/{reportCaseId}/resolve-case")
    public ReportCaseDTO resolveCase(@PathVariable Long reportCaseId) {
        return reportManagerService.resolveCase(reportCaseId);
    }

    @PatchMapping("/{reportCaseId}/request-elevation")
    public ResponseEntity<Void> requestElevation(@PathVariable Long reportCaseId) {
        reportManagerService.requestElevation(reportCaseId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{reportCaseId}/warn")
    public ResponseEntity<Void> warnUser(@RequestBody WarnUserRequestDTO warnRequestDTO,
                                         @PathVariable Long reportCaseId) {
        reportManagerService.warnUser(warnRequestDTO, reportCaseId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{reportCaseId}/ban")
    public ResponseEntity<Void> banUser(@RequestBody BanRequestDTO banRequestDTO, @PathVariable Long reportCaseId) {
        reportManagerService.banUser(reportCaseId, banRequestDTO);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{reportCaseId}/ncmec-report")
    public ResponseEntity<ReportCaseDTO> submitNcmecReport(
            @PathVariable Long reportCaseId,
            @Valid @RequestBody NcmecReportRequestDTO request) {

        User currentUser = securityService.getCurrentUser();

        com.mk3.chatapp.dtos.responses.ReportCaseDTO report = reportManagerService.submitNcmecReport(
                reportCaseId,
                request.incidentType(),
                request.reportAnnotations() != null ? request.reportAnnotations() : Collections.emptyList(),
                request.fileAnnotations() != null ? request.fileAnnotations() : Collections.emptyList(),
                currentUser);

        return ResponseEntity.ok(report);
    }

    @DeleteMapping("/{reportCaseId}/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long reportCaseId, @PathVariable Long messageId) {
        reportManagerService.deleteMessage(reportCaseId, messageId);
        return ResponseEntity.noContent().build();
    }

}
