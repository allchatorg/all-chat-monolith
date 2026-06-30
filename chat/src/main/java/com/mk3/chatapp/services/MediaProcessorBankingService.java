package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.MediaProcessorBankAttachmentRequestDTO;
import com.mk3.chatapp.dtos.responses.MediaProcessorBankAttachmentResponseDTO;

public interface MediaProcessorBankingService {

    MediaProcessorBankAttachmentResponseDTO bankAttachment(byte[] fileContent,
                                                           MediaProcessorBankAttachmentRequestDTO request);
}
