package com.mk3.chatapp.utils;

import java.util.Set;

/** Shared allowlist for standalone stickers, inline emojis, and character reactions. */
public final class ProCharacterCatalog {
    private static final Set<String> IDS = Set.of(
            "wojak", "soyjak", "chud", "chad-1", "chad-2", "virgin", "doomer", "coomer",
            "bloomer", "zoomer", "npc", "grug", "pepe", "apu-apustaja", "honkler", "spurdo", "gondola",
            "big-brain-wojak", "dumb-wojak", "gigachad", "rage-pepe", "smug-pepe", "smug-wojak", "soyjak-2");

    private ProCharacterCatalog() {
    }

    public static boolean contains(String id) {
        return id != null && IDS.contains(id);
    }
}
