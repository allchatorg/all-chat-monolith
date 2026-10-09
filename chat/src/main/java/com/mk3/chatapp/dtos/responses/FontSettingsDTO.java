package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.FontPreset;
import java.time.Instant;

public record FontSettingsDTO(FontPreset usernameFont, FontPreset messageFont, long fontRevision,
                              boolean vipActive, int dailyLimit, int changesRemaining, Instant resetsAt) {
}
