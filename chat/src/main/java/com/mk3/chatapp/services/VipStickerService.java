package com.mk3.chatapp.services;

import com.mk3.chatapp.utils.VipCharacterCatalog;
import com.mk3.chatapp.utils.ChatMessageContent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class VipStickerService {
    private final VipBadgeService vipBadgeService;

    public void validateForSend(String stickerId, Long userId) {
        if (!VipCharacterCatalog.contains(stickerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown sticker");
        }
        if (!vipBadgeService.hasActiveEntitlement(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "allchat VIP is required to send this sticker");
        }
    }

    /** Both sticker and inline emoji uses of the character catalog share the same entitlement. */
    public void validateInlineEmojis(String content, String previousContent, Long userId) {
        Map<String, Integer> previousCounts = ChatMessageContent.customEmojiCounts(previousContent);
        boolean addedPaidEmoji = false;
        for (Map.Entry<String, Integer> emoji : ChatMessageContent.customEmojiCounts(content).entrySet()) {
            if (emoji.getValue() <= previousCounts.getOrDefault(emoji.getKey(), 0)) {
                continue;
            }
            if (!VipCharacterCatalog.contains(emoji.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown custom emoji");
            }
            addedPaidEmoji = true;
        }
        // Expiry must not prevent retaining, moving, or removing existing
        // emojis. Only an increase in a particular identity requires VIP.
        if (addedPaidEmoji && !vipBadgeService.hasActiveEntitlement(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "allchat VIP is required to send this emoji");
        }
    }
}
