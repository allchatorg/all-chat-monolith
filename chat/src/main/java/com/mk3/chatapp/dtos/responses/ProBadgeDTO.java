package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.FontPreset;

/** Public presentation only: never exposes a hidden subscription or billing details. */
public record ProBadgeDTO(Long userId, boolean proBadgeVisible, long proBadgeRevision,
                          FontPreset usernameFont, FontPreset messageFont, long fontRevision) {
}
