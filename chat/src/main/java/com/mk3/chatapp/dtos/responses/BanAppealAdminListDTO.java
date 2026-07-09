package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.BanAppealStatus;
import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;

import java.time.Instant;

public record BanAppealAdminListDTO(
        Long id,
        BanAppealStatus status,
        Instant submittedAt,
        Long userId,
        String username,
        Long banId,
        BanType banType,
        ReportType reportType,
        String banDescription,
        Long bannedByUserId,
        String bannedByUsername,
        Long reviewerUserId,
        String reviewerUsername,
        Instant resolvedAt
) {
}
