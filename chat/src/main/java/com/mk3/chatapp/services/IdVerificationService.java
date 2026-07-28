package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.IdVerificationSessionResponseDTO;
import com.mk3.chatapp.dtos.responses.IdVerificationStatusResponseDTO;

public interface IdVerificationService {

    void requireIdVerification(Long targetUserId, Long reportCaseId);

    void clearIdVerificationRequirement(Long targetUserId);

    IdVerificationSessionResponseDTO createVerificationSession();

    IdVerificationStatusResponseDTO getOwnStatus();

    void handleWebhookEvent(String payload, String signatureHeader);

    void promoteEligibleUnderageUsers();
}
