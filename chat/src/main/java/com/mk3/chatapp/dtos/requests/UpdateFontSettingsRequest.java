package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.FontPreset;
import jakarta.validation.constraints.NotNull;

public record UpdateFontSettingsRequest(@NotNull FontPreset usernameFont, @NotNull FontPreset messageFont) {
}
