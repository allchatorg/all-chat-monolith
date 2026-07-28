package com.mk3.chatapp.dtos.responses;

public record IdVerificationResultDTO(
        Long userId,
        Long reportCaseId,
        boolean passed) {
}
