package com.mk3.chatapp.dtos.requests;

public record ReactionRequestDTO(
        Long messageId,
        String emoji,
        String emojiId
) {
}
