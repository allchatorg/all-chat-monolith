package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.dtos.BanResponseDTO;

import java.time.Instant;
import java.util.List;

public record BanAppealAdminDetailDTO(
        BanAppealAdminListDTO summary,
        String appealText,
        String whatWillChange,
        String internalNote,
        String userFacingMessage,
        Long resolvedByUserId,
        String resolvedByUsername,
        Instant banCreatedAt,
        Instant banExpiresAt,
        boolean banActive,
        int priorBanCount,
        List<BanResponseDTO> priorBans
) {
}
