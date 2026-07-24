package com.mk3.chatapp.dtos.responses;

public record IdVerificationRequiredDTO(
        Long userId,
        Long reportCaseId) {
}
