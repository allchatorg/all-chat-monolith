package com.mk3.chatapp.services;

import com.mk3.chatapp.utils.ProCharacterCatalog;
import com.mk3.chatapp.utils.ChatMessageContent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProStickerService {
    private final ProBadgeService proBadgeService;

    public void validateForSend(String stickerId, Long userId) {
        if (!ProCharacterCatalog.contains(stickerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown sticker");
        }
        if (!proBadgeService.hasActiveEntitlement(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "allchat Pro is required to send this sticker");
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
            if (!ProCharacterCatalog.contains(emoji.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown custom emoji");
            }
            addedPaidEmoji = true;
        }
        // Expiry must not prevent retaining, moving, or removing existing
        // emojis. Only an increase in a particular identity requires Pro.
        if (addedPaidEmoji && !proBadgeService.hasActiveEntitlement(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "allchat Pro is required to send this emoji");
        }
    }
}
