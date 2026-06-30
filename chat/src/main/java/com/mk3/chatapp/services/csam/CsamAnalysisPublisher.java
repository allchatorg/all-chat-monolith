package com.mk3.chatapp.services.csam;

import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.identity.User;

public interface CsamAnalysisPublisher {
    void publishAttachmentForAnalysis(Attachment attachment, User uploader);
}
