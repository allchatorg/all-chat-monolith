package com.mk3.chatapp.dtos.responses;

/** Public presentation only: never exposes a hidden subscription or billing details. */
public record ProBadgeDTO(Long userId, boolean proBadgeVisible, long proBadgeRevision) {
}
