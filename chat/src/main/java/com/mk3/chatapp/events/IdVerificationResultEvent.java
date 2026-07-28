package com.mk3.chatapp.events;

public record IdVerificationResultEvent(Long userId, Long reportCaseId, boolean passed) {
}
