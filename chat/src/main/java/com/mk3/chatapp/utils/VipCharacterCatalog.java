package com.mk3.chatapp.utils;

import java.util.Set;

/** Shared allowlist for standalone stickers, inline emojis, and character reactions. */
public final class VipCharacterCatalog {
    private static final Set<String> IDS = Set.of(
            "catapuss-cheer", "catapuss-typing", "catapuss-love", "catapuss-heart-eyes", "catapuss-laugh",
            "catapuss-cry", "catapuss-sad", "catapuss-angry", "catapuss-rage", "catapuss-shock", "catapuss-think",
            "catapuss-smart", "catapuss-smug", "catapuss-cool", "catapuss-strong", "catapuss-shy", "catapuss-sleepy",
            "catapuss-party", "catapuss-confused", "catapuss-blank", "catapuss-silly", "catapuss-thumbs-up",
            "catapuss-wave", "catapuss-please", "catapuss-peek");

    private VipCharacterCatalog() {
    }

    public static boolean contains(String id) {
        return id != null && IDS.contains(id);
    }
}
