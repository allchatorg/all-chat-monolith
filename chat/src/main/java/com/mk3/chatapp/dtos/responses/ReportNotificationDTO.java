package com.mk3.chatapp.dtos.responses;

public record ReportNotificationDTO(
        Long reporterId,
        Long reportCaseId,
        ReportCaseDTO reportCase) {
}
