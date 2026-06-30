package com.mk3.chatapp.dtos.responses;

import java.util.Set;

public record ReactionDetailsDTO(
        Long id,
        Long messageId,
        String emoji,
        String emojiId,
        Integer usersCount,
        Set<UserMinimalDTO> users
) {
}
