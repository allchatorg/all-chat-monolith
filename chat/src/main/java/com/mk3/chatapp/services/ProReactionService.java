package com.mk3.chatapp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProReactionService {
    private static final String PREFIX = "allchat:";
    private static final Set<String> CHARACTER_IDS = Set.of(
            "wojak", "soyjak", "chud", "chad-1", "chad-2", "virgin", "doomer", "coomer",
            "bloomer", "zoomer", "npc", "grug", "pepe", "apu-apustaja", "honkler", "spurdo", "gondola");

    private final ProBadgeService proBadgeService;

    public void validateForAdd(String emoji, String emojiId, Long userId) {
        if (validateIdentity(emoji, emojiId) && !proBadgeService.hasActiveEntitlement(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "allchat Pro is required to add this reaction");
        }
    }

    /** Canonical identity is still required for removal, but a subscription is not. */
    public boolean validateIdentity(String emoji, String emojiId) {
        boolean custom = isCustom(emoji) || isCustom(emojiId);
        if (custom && (!isCustom(emoji) || !emoji.equals(emojiId)
                || !CHARACTER_IDS.contains(emoji.substring(PREFIX.length())))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or mismatched custom reaction");
        }
        return custom;
    }

    private boolean isCustom(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
