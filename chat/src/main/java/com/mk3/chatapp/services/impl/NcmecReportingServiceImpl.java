package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.MediaProcessorBankAttachmentRequestDTO;
import com.mk3.chatapp.dtos.responses.MediaProcessorBankAttachmentResponseDTO;
import com.mk3.chatapp.enums.*;
import com.mk3.chatapp.exceptions.NcmecReportingException;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.models.ncmec.*;
import com.mk3.chatapp.repositories.LastSessionInfoRepository;
import com.mk3.chatapp.repositories.NcmecReportAuditLogRepository;
import com.mk3.chatapp.services.FileUploadService;
import com.mk3.chatapp.services.MediaProcessorBankingService;
import com.mk3.chatapp.services.NcmecReportingService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NcmecReportingServiceImpl implements NcmecReportingService {

    private final LastSessionInfoRepository lastSessionInfoRepository;
    private final RestTemplate restTemplate;
    private final FileUploadService fileUploadService;
    private final MediaProcessorBankingService mediaProcessorBankingService;
    private final NcmecReportAuditLogRepository ncmecReportAuditLogRepository;

    @Value("${ncmec.username}")
    private String ncmecUsername;

    @Value("${ncmec.password}")
    private String ncmecPassword;

    @Value("${ncmec.submit-url}")
    private String submitUrl;

    @Override
    public Long openReport(ReportCase reportCase, NcmecIncidentType incidentType,
                           List<NcmecReportAnnotation> reportAnnotations, List<NcmecFileAnnotation> fileAnnotations,
                           User initiatingUser) {

        log.info("Starting NCMEC report orchestration for ReportCase id={}", reportCase.getId());

        Long ncmecReportId = null;

        try {
            // 1. Open the report (POST /submit)
            NcmecReport ncmecReport = buildNcmecReport(reportCase, incidentType, reportAnnotations, initiatingUser);
            String xml = marshalToXml(ncmecReport);
            log.debug("Marshalled NCMEC report XML:\n{}", xml);

            // Create initial audit log
            NcmecReportAuditLog auditLog = NcmecReportAuditLog.builder()
                    .reportCaseId(reportCase.getId())
                    .xmlContent(xml)
                    .status("PENDING")
                    .logType(AuditLogType.NCMEC_REPORT)
                    .action("NCMEC_REPORT_SUBMISSION")
                    .description("NCMEC report for case #" + reportCase.getId())
                    .targetUserId(reportCase.getReports().get(0).getReportedUser().getId())
                    .build();
            auditLog = ncmecReportAuditLogRepository.save(auditLog);

            String responseXml = sendOpenReportRequest(xml);
            NcmecReportResponse response = parseResponse(responseXml);

            if (response.getResponseCode() != 0) {
                log.error("NCMEC report submission failed: code={}, description={}", response.getResponseCode(),
                        response.getResponseDescription());
                auditLog.setStatus("FAILED");
                auditLog.setDescription("Submission failed: " + response.getResponseDescription());
                ncmecReportAuditLogRepository.save(auditLog);
                throw new NcmecReportingException("NCMEC report submission failed: " + response.getResponseDescription());
            }
            ncmecReportId = response.getReportId();
            log.info("NCMEC report opened successfully. reportId={}", ncmecReportId);

            auditLog.setNcmecReportId(ncmecReportId);
            auditLog.setStatus("SUBMITTED"); // Or 'OPEN'
            ncmecReportAuditLogRepository.save(auditLog);

            // 2. Upload Files (if any)
            if (reportCase.getMessage().getAttachments() != null
                    && !reportCase.getMessage().getAttachments().isEmpty()) {
                for (Attachment attachment : reportCase.getMessage().getAttachments()) {
                    uploadFile(ncmecReportId, attachment);
                }
            }

            // 3. Finish the report (POST /finish)
            finishReport(ncmecReportId);

            return ncmecReportId;

        } catch (Exception e) {
            log.error("Failed to complete NCMEC report orchestration", e);
            if (ncmecReportId != null) {
                try {
                    cancelReport(ncmecReportId);
                } catch (Exception cancelEx) {
                    log.error("Failed to rollback (cancel) report after failure", cancelEx);
                }
            }
            throw toNcmecReportingException("Failed to process NCMEC report", e);
        }
    }

    @Override
    public void uploadFile(Long reportId, Attachment attachment) {
        log.info("Uploading file for reportId={}", reportId);

        try {
            byte[] fileContent = fileUploadService.getFileContent(attachment.getUrl());
            if (shouldBankInMediaProcessor(attachment)) {
                bankAttachmentInMediaProcessor(attachment, fileContent);
            } else {
                log.info("Skipping media-processor banking for unsupported attachment type. attachmentId={}",
                        attachment.getId());
            }

            ByteArrayResource resource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return attachment.getName();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("id", reportId);
            body.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", buildBasicAuthHeader());
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String uploadUrl = submitUrl.replace("/submit", "/upload");
            String responseXml = postNcmec(uploadUrl, requestEntity, "upload file");

            log.debug("NCMEC upload response:\n{}", responseXml);

            NcmecReportResponse response = parseResponse(responseXml);
            if (response.getResponseCode() != 0) {
                log.error("NCMEC file upload failed: code={}, description={}", response.getResponseCode(),
                        response.getResponseDescription());
                throw new NcmecReportingException("NCMEC file upload failed: " + response.getResponseDescription());
            }

            log.info("File uploaded successfully. fileId={}", response.getFileId());

        } catch (Exception e) {
            log.error("Failed to upload file to NCMEC", e);
            throw toNcmecReportingException("Failed to upload file to NCMEC", e);
        }
    }

    private void bankAttachmentInMediaProcessor(Attachment attachment, byte[] fileContent) {
        try {
            MediaProcessorBankAttachmentResponseDTO bankResponse = mediaProcessorBankingService.bankAttachment(
                    fileContent,
                    new MediaProcessorBankAttachmentRequestDTO(
                            attachment.getId(),
                            attachment.getName(),
                            attachment.getMime() == null ? null : attachment.getMime().getMime(),
                            attachment.getAttachmentType() == null ? null : attachment.getAttachmentType().getFileType(),
                            null));

            log.info("Banked attachment in media-processor. attachmentId={} bankName={} bankContentId={}",
                    attachment.getId(),
                    bankResponse.bankName(),
                    bankResponse.bankContentId());
        } catch (Exception e) {
            log.error("Failed to bank attachment in media-processor before NCMEC upload. attachmentId={} sizeBytes={}",
                    attachment.getId(),
                    fileContent == null ? null : fileContent.length,
                    e);
            throw new NcmecReportingException("Failed to bank attachment in media-processor before NCMEC upload", e);
        }
    }

    private boolean shouldBankInMediaProcessor(Attachment attachment) {
        if (attachment == null || attachment.getAttachmentType() == null) {
            return false;
        }

        AttachmentTypeEnum fileType = attachment.getAttachmentType().getFileType();
        return fileType == AttachmentTypeEnum.IMAGE || fileType == AttachmentTypeEnum.VIDEO;
    }

    @Override
    public void finishReport(Long reportId) {
        log.info("Finishing (submitting) reportId={}", reportId);

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("id", reportId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", buildBasicAuthHeader());
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String finishUrl = submitUrl.replace("/submit", "/finish");
            String responseXml = postNcmec(finishUrl, requestEntity, "finish report");

            log.debug("NCMEC finish response:\n{}", responseXml);

            // We can parse it as ReportDoneResponse if we have the class, or generic check
            // For now we check for error strings or use generic validaton if possible.
            // However, the docs say it returns <reportDoneResponse>
            if (responseXml == null || !responseXml.contains("<responseCode>0</responseCode>")) {
                log.error("NCMEC finish report failed. Response: {}", responseXml);
                throw new NcmecReportingException("NCMEC finish report failed");
            }

            log.info("Report finished successfully. reportId={}", reportId);

        } catch (Exception e) {
            log.error("Failed to finish report", e);
            throw toNcmecReportingException("Failed to finish report", e);
        }
    }

    @Override
    public void cancelReport(Long reportId) {
        log.info("Cancelling (retracting) reportId={}", reportId);

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("id", reportId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", buildBasicAuthHeader());
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String retractUrl = submitUrl.replace("/submit", "/retract");
            String responseXml = postNcmec(retractUrl, requestEntity, "retract report");

            log.debug("NCMEC retract response:\n{}", responseXml);

            NcmecReportResponse response = parseResponse(responseXml);
            if (response.getResponseCode() != 0) {
                log.error("NCMEC retract report failed: code={}, description={}", response.getResponseCode(),
                        response.getResponseDescription());
                throw new NcmecReportingException("NCMEC retract report failed: " + response.getResponseDescription());
            }

            log.info("Report cancelled successfully. reportId={}", reportId);

            ncmecReportAuditLogRepository.findByNcmecReportId(reportId).ifPresent(al -> {
                al.setStatus("RETRACTED");
                al.setUpdatedAt(Instant.now());
                ncmecReportAuditLogRepository.save(al);
            });

        } catch (Exception e) {
            log.error("Failed to cancel report", e);
            throw toNcmecReportingException("Failed to cancel report", e);
        }
    }

    // ── XML Marshalling / Unmarshalling ─────────────────────────────────

    private String marshalToXml(NcmecReport report) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(NcmecReport.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        StringWriter writer = new StringWriter();
        marshaller.marshal(report, writer);
        return writer.toString();
    }

    private NcmecReportResponse parseResponse(String xml) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(NcmecReportResponse.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (NcmecReportResponse) unmarshaller.unmarshal(new StringReader(xml));
    }

    // ── HTTP Submission ─────────────────────────────────────────────────

    private String sendOpenReportRequest(String xml) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "xml", StandardCharsets.UTF_8));
        headers.set("Authorization", buildBasicAuthHeader());

        HttpEntity<String> request = new HttpEntity<>(xml, headers);
        String response = postNcmec(submitUrl, request, "submit report");

        log.debug("NCMEC submit response:\n{}", response);
        return response;
    }

    private String postNcmec(String url, HttpEntity<?> request, String operation) {
        try {
            return restTemplate.postForObject(url, request, String.class);
        } catch (RestClientResponseException e) {
            log.error("NCMEC {} HTTP failure. url={} status={} responseBody={}",
                    operation,
                    url,
                    e.getStatusCode(),
                    truncateResponseBody(e.getResponseBodyAsString()),
                    e);
            throw new NcmecReportingException("NCMEC " + operation + " failed with HTTP " + e.getStatusCode(), e);
        }
    }

    private String truncateResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "<empty>";
        }
        int maxLength = 2_000;
        return responseBody.length() <= maxLength
                ? responseBody
                : responseBody.substring(0, maxLength) + "...";
    }

    private NcmecReportingException toNcmecReportingException(String message, Exception e) {
        if (e instanceof NcmecReportingException ncmecException) {
            return ncmecException;
        }
        return new NcmecReportingException(message, e);
    }

    private String buildBasicAuthHeader() {
        String credentials = ncmecUsername + ":" + ncmecPassword;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    // ── Report Building ─────────────────────────────────────────────────

    private NcmecReport buildNcmecReport(ReportCase reportCase, NcmecIncidentType incidentType,
                                         List<NcmecReportAnnotation> reportAnnotations, User initiatingUser) {

        Message message = reportCase.getMessage();
        // The reported user comes from the first Report in the case
        User reportedUser = reportCase.getReports().get(0).getReportedUser();

        return NcmecReport.builder().incidentSummary(buildIncidentSummary(reportCase, incidentType, reportAnnotations))
                .internetDetails(List.of(buildInternetDetails(message))).reporter(buildReporter(initiatingUser))
                .personOrUserReported(buildPersonOrUserReported(reportedUser)).build();
    }

    // ── IncidentSummary ──────────────────────────────────────────────────

    private IncidentSummary buildIncidentSummary(ReportCase reportCase, NcmecIncidentType incidentType,
                                                 List<NcmecReportAnnotation> reportAnnotations) {

        OffsetDateTime incidentDateTime = reportCase.getCreatedAt().atOffset(ZoneOffset.UTC);

        return IncidentSummary.builder().incidentType(incidentType.getDescription()).incidentDateTime(incidentDateTime)
                .reportAnnotations(buildReportAnnotations(reportAnnotations)).build();
    }

    private ReportAnnotations buildReportAnnotations(List<NcmecReportAnnotation> annotations) {
        ReportAnnotations.ReportAnnotationsBuilder builder = ReportAnnotations.builder();
        if (annotations == null) {
            return builder.build();
        }

        for (NcmecReportAnnotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            switch (annotation) {
                case SEXTORTION -> builder.sextortion(true);
                case CSAM_SOLICITATION -> builder.csamSolicitation(true);
                case MINOR_TO_MINOR -> builder.minorToMinorInteraction(true);
                case SPAM -> builder.spam(true);
                case SADISTIC_EXPLOITATION -> builder.sadisticOnlineExploitation(true);
            }
        }

        return builder.build();
    }

    // ── InternetDetails ──────────────────────────────────────────────────

    private InternetDetails buildInternetDetails(Message message) {
        ChatImIncident chatImIncident = ChatImIncident.builder().chatClient("AllChat")
                .chatRoomName(message.getChatRoom().getName()).content(message.getContent()).build();

        return InternetDetails.builder().chatImIncident(chatImIncident).build();
    }

    // ── Reporter ─────────────────────────────────────────────────────────

    private Reporter buildReporter(User initiatingUser) {
        ReportingPerson.ReportingPersonBuilder personBuilder = ReportingPerson.builder()
                .email(initiatingUser.getEmail());

        if (initiatingUser.getPhoneNumber() != null && !initiatingUser.getPhoneNumber().isBlank()) {
            personBuilder.phone(Phone.builder().phoneNumber(initiatingUser.getPhoneNumber()).build());
        }

        return Reporter.builder().reportingPerson(personBuilder.build()).build();
    }

    // ── PersonOrUserReported ─────────────────────────────────────────────

    private PersonOrUserReported buildPersonOrUserReported(User reportedUser) {
        PersonOrUserReported.PersonOrUserReportedBuilder builder = PersonOrUserReported.builder()
                .espIdentifier(reportedUser.getId().toString()).screenName(reportedUser.getApplicationUsername());

        // Populate person details if available
        PersonOrUserReportedPerson.PersonOrUserReportedPersonBuilder personBuilder = PersonOrUserReportedPerson
                .builder();
        boolean hasPersonDetails = false;

        if (reportedUser.getEmail() != null && !reportedUser.getEmail().isBlank()) {
            personBuilder.email(reportedUser.getEmail());
            hasPersonDetails = true;
        }
        if (hasPersonDetails) {
            builder.personOrUserReportedPerson(personBuilder.build());
        }

        StringBuilder additionalInfo = new StringBuilder();
        if (reportedUser.getPhoneNumber() != null && !reportedUser.getPhoneNumber().isBlank()) {
            additionalInfo.append("Reported user's phone: ").append(reportedUser.getPhoneNumber());
        }

        // Phone is not valid in personOrUserReportedPerson for ISPWS schema; include it as note instead.
        lastSessionInfoRepository.findById(reportedUser.getId()).ifPresent(session -> {
            if (additionalInfo.length() > 0) {
                additionalInfo.append(" | ");
            }
            additionalInfo.append("Last known IP: ").append(session.getIpAddress());
        });
        if (additionalInfo.length() > 0) {
            builder.additionalInfo(additionalInfo.toString());
        }

        return builder.build();
    }
}
