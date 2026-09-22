package com.mk3.chatapp.enums;

/** The public contract contains preset IDs, never arbitrary font names or CSS. */
public enum FontPreset {
    DEFAULT,
    INTER,
    OPEN_SANS,
    NUNITO,
    COMFORTAA,
    CAVEAT;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static FontPreset fromJson(String value) {
        return FontPreset.valueOf(value);
    }
}
