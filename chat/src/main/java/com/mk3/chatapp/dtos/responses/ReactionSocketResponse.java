package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.ReactionType;

public record ReactionSocketResponse(
        Long reactionId,
        Long chatroomId,
        Long messageId,
        ReactionType responseType,
        String emoji,
        String emojiId,
        UserMinimalDTO reactedBy
) {
}
