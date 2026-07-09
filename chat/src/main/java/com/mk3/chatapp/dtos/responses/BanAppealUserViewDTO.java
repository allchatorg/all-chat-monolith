package com.mk3.chatapp.dtos.responses;

import java.time.Instant;

/**
 * Banned-user view of their own appeal. Deliberately excludes reviewer identity,
 * internal notes and anything that could identify staff or evidence.
 */
public record BanAppealUserViewDTO(
        Long id,
        String status,
        Instant submittedAt,
        Instant resolvedAt,
        String appealText,
        String whatWillChange,
        String userFacingMessage
) {
}
