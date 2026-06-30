package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.ReportOrigin;
import com.mk3.chatapp.enums.ReportType;

public record ReportDTO(
        Long id,
        Long reportCaseId,
        Long messageId,
        Long reportedUserId,
        UserDTO reporter,
        ReportOrigin reporterOrigin,
        ReportType reportType,
        String createdAt,
        String description) {
}
