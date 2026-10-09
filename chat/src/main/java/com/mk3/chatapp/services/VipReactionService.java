package com.mk3.chatapp.services;

import com.mk3.chatapp.utils.VipCharacterCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class VipReactionService {
    private static final String PREFIX = "allchat:";
    private final VipBadgeService vipBadgeService;

    public void validateForAdd(String emoji, String emojiId, Long userId) {
        if (validateIdentity(emoji, emojiId) && !vipBadgeService.hasActiveEntitlement(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "allchat VIP is required to add this reaction");
        }
    }

    /** Canonical identity is still required for removal, but a subscription is not. */
    public boolean validateIdentity(String emoji, String emojiId) {
        boolean custom = isCustom(emoji) || isCustom(emojiId);
        if (custom && (!isCustom(emoji) || !emoji.equals(emojiId)
                || !VipCharacterCatalog.contains(emoji.substring(PREFIX.length())))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or mismatched custom reaction");
        }
        return custom;
    }

    private boolean isCustom(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
