package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

public record BanRequestDTO(
        @NotNull Long userId,
        Duration duration,
        @NotNull ReportType reportType,
        @NotNull BanType banType,
        String description,
        @NotNull boolean deleteMessages,
        Duration deleteMessagesDuration,
        Long reportCaseId
) {
}
