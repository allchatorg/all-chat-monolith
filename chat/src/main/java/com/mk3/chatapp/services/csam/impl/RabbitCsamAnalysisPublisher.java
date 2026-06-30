package com.mk3.chatapp.services.csam.impl;

import com.mk3.chatapp.configs.messaging.CsamMessagingProperties;
import com.mk3.chatapp.enums.AttachmentTypeEnum;
import com.mk3.chatapp.messaging.csam.CsamAnalysisRequestMessage;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.FileUploadService;
import com.mk3.chatapp.services.csam.CsamAnalysisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "csam.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitCsamAnalysisPublisher implements CsamAnalysisPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final CsamMessagingProperties properties;
    private final FileUploadService fileUploadService;

    @Override
    public void publishAttachmentForAnalysis(Attachment attachment, User uploader) {
        if (!shouldAnalyze(attachment)) {
            return;
        }

        CsamAnalysisRequestMessage message = new CsamAnalysisRequestMessage(
                attachment.getId(),
                uploader.getId(),
                attachment.getName(),
                attachment.getSize(),
                attachment.getUrl(),
                fileUploadService.getFileUrl(attachment.getUrl()),
                attachment.getMime().getMime(),
                attachment.getAttachmentType().getFileType(),
                attachment.getCreatedAt());

        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.analysisRequestRoutingKey(),
                message);

        log.debug("Queued attachment for CSAM analysis. attachmentId={}", attachment.getId());
    }

    private boolean shouldAnalyze(Attachment attachment) {
        if (attachment == null || attachment.getAttachmentType() == null) {
            return false;
        }

        AttachmentTypeEnum fileType = attachment.getAttachmentType().getFileType();
        return fileType == AttachmentTypeEnum.IMAGE || fileType == AttachmentTypeEnum.VIDEO;
    }
}
