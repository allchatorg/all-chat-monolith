package com.mk3.chatapp.dtos;

import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;

public record BanResponseDTO(
        Long id,
        Long userId,
        String username,
        String ipAddress,
        String userAgent,
        String description,
        String expiresAt,
        boolean active,
        BanType type,
        ReportType reportType
) {
}
