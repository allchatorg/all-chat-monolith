package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.ReportType;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        Long messageId,
        ReportType reportType,
        @Size(max = 500, message = "Description must be at most 500 characters") String description) {
}
