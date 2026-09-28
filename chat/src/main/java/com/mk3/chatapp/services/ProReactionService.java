package com.mk3.chatapp.services;

import com.mk3.chatapp.utils.ProCharacterCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProReactionService {
    private static final String PREFIX = "allchat:";
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
                || !ProCharacterCatalog.contains(emoji.substring(PREFIX.length())))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or mismatched custom reaction");
        }
        return custom;
    }

    private boolean isCustom(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
