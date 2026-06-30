package com.mk3.chatapp.dtos.responses;

public record ReactionSummaryDTO(
        Long id,
        Long messageId,
        String emoji,
        String emojiId,
        Integer usersCount,
        boolean reactedByCurrentUser
) {
}


