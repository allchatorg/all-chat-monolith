package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.FontPreset;

public record UserMinimalDTO(
        Long id,
        String username,
        boolean vipBadgeVisible,
        long vipBadgeRevision,
        FontPreset usernameFont,
        FontPreset messageFont,
        long fontRevision
) {
    public UserMinimalDTO(Long id, String username, boolean vipBadgeVisible, long vipBadgeRevision) {
        this(id, username, vipBadgeVisible, vipBadgeRevision, FontPreset.DEFAULT, FontPreset.DEFAULT, 0L);
    }
}
