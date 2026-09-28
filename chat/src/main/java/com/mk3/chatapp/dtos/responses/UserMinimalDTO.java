package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.FontPreset;

public record UserMinimalDTO(
        Long id,
        String username,
        boolean proBadgeVisible,
        long proBadgeRevision,
        FontPreset usernameFont,
        FontPreset messageFont,
        long fontRevision
) {
    public UserMinimalDTO(Long id, String username, boolean proBadgeVisible, long proBadgeRevision) {
        this(id, username, proBadgeVisible, proBadgeRevision, FontPreset.DEFAULT, FontPreset.DEFAULT, 0L);
    }
}
