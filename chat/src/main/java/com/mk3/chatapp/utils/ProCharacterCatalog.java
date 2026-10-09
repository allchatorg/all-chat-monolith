package com.mk3.chatapp.utils;

import java.util.Set;

/** Shared allowlist for standalone stickers, inline emojis, and character reactions. */
public final class ProCharacterCatalog {
    private static final Set<String> IDS = Set.of(
            "catpuss-cheer", "catpuss-typing", "catpuss-love", "catpuss-heart-eyes", "catpuss-laugh",
            "catpuss-cry", "catpuss-sad", "catpuss-angry", "catpuss-rage", "catpuss-shock", "catpuss-think",
            "catpuss-smart", "catpuss-smug", "catpuss-cool", "catpuss-strong", "catpuss-shy", "catpuss-sleepy",
            "catpuss-party", "catpuss-confused", "catpuss-blank", "catpuss-silly", "catpuss-thumbs-up",
            "catpuss-wave", "catpuss-please", "catpuss-peek");

    private ProCharacterCatalog() {
    }

    public static boolean contains(String id) {
        return id != null && IDS.contains(id);
    }
}
