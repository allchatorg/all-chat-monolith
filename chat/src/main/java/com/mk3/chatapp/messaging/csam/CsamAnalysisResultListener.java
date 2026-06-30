package com.mk3.chatapp.messaging.csam;

import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.services.AttachmentService;
import com.mk3.chatapp.services.ReportManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "csam.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CsamAnalysisResultListener {

    private static final int REPORT_DESCRIPTION_MAX_LENGTH = 500;

    private final ReportManagerService reportManagerService;
    private final AttachmentService attachmentService;

    @RabbitListener(queues = "${csam.messaging.analysis-result-queue}")
    public void onAnalysisResult(CsamAnalysisResultMessage message) {
        if (message == null || (message.messageId() == null && message.attachmentId() == null)) {
            log.warn("Received CSAM analysis result without a message id or attachment id.");
            return;
        }

        if (message.reportsCsam()) {
            createSystemReport(message);
            return;
        }

        log.debug("Received CSAM analysis result. attachmentId={}", message.attachmentId());
    }

    private void createSystemReport(CsamAnalysisResultMessage analysisResult) {
        Long messageId = resolveMessageId(analysisResult);
        if (messageId == null) {
            log.warn(
                    "Positive CSAM analysis result could not be tied to a message. attachmentId={} workerReference={}",
                    analysisResult.attachmentId(),
                    analysisResult.workerReference());
            return;
        }

        try {
            reportManagerService.createSystemReportForMessage(
                    messageId,
                    ReportType.REAL_CHILD_SEXUAL_ABUSE_MATERIAL,
                    buildReportDescription(analysisResult));
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Positive CSAM analysis referenced a message that could not be reported. messageId={} attachmentId={}",
                    messageId,
                    analysisResult.attachmentId());
            return;
        }

        log.info(
                "Created or updated system CSAM report case. messageId={} attachmentId={} workerReference={}",
                messageId,
                analysisResult.attachmentId(),
                analysisResult.workerReference());
    }

    private Long resolveMessageId(CsamAnalysisResultMessage analysisResult) {
        if (analysisResult.messageId() != null) {
            return analysisResult.messageId();
        }

        try {
            Attachment attachment = attachmentService.findById(analysisResult.attachmentId());
            return attachment.getMessage() != null ? attachment.getMessage().getId() : null;
        } catch (IllegalArgumentException ex) {
            log.warn("Positive CSAM analysis referenced an unknown attachment. attachmentId={}",
                    analysisResult.attachmentId());
            return null;
        }
    }

    private String buildReportDescription(CsamAnalysisResultMessage analysisResult) {
        return "Automated safety scanning detected a positive CSAM result for this message. " +
                "Please review it closely and submit a report to NCMEC if necessary.";
    }
}
