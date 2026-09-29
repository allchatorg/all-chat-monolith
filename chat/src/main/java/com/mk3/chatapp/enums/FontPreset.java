package com.mk3.chatapp.enums;

/** The public contract contains preset IDs, never arbitrary font names or CSS. */
public enum FontPreset {
    DEFAULT,
    ROBOTO,
    PETIT_FORMAL_SCRIPT,
    ITALIANNO,
    KABLAMMO,
    CRAFTY_GIRLS,
    EMILYS_CANDY,
    EVELYNE,
    MESSY_HANDWRITTEN,
    LOVE_LIGHT,
    CLICKER_SCRIPT,
    TANGERINE,
    SAHIR_YESTA,
    GISTA_DANES,
    A_YUMMY_APOLOGY,
    PRINCESS_SOFIA,
    RAIN_KISS,
    SWEET_VALENTINE,
    HIBIS,
    STICKER,
    ROSAVINE,
    BHIGLI,

    // Retain old names so existing database rows and serialized sessions still load.
    @Deprecated
    INTER,
    @Deprecated
    OPEN_SANS,
    @Deprecated
    NUNITO,
    @Deprecated
    COMFORTAA,
    @Deprecated
    CAVEAT;

    public boolean isSelectable() {
        return switch (this) {
            // Enable these once their licensed webfont assets are bundled in the frontend.
            case SWEET_VALENTINE, HIBIS, STICKER, ROSAVINE, BHIGLI -> false;
            case INTER, OPEN_SANS, NUNITO, COMFORTAA, CAVEAT -> false;
            default -> true;
        };
    }

    public FontPreset availableOrDefault() {
        return isSelectable() ? this : DEFAULT;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static FontPreset fromJson(String value) {
        FontPreset preset = FontPreset.valueOf(value);
        if (!preset.isSelectable()) {
            throw new IllegalArgumentException("This font preset is no longer available");
        }
        return preset;
    }
}
