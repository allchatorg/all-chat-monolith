package com.mk3.chatapp.services.csam.impl;

import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.csam.CsamAnalysisPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "csam.messaging", name = "enabled", havingValue = "false")
public class NoOpCsamAnalysisPublisher implements CsamAnalysisPublisher {
    @Override
    public void publishAttachmentForAnalysis(Attachment attachment, User uploader) {
        // Messaging can be disabled in local/dev profiles without changing the upload flow.
    }
}
