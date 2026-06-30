package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;

import java.time.Instant;

public record BanUserMessageDTO(
        ReportType reportType,
        BanType type,
        boolean permanent,
        Instant expiresAt,
        String description
) {
}
