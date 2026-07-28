package com.mk3.chatapp.events;

public record IdVerificationRequiredEvent(Long userId, Long reportCaseId) {
}
