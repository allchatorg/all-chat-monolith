package com.mk3.chatapp.dtos.responses;

import java.time.Instant;

/**
 * Admin/moderation view of a single private conversation belonging to a reviewed user.
 * Unlike {@link PrivateChatDTO} (which is relative to the current user), this exposes
 * BOTH participants so a staff member observing the conversation sees who is talking to whom.
 */
public record AdminConversationDTO(
        Long roomId,
        UserMinimalDTO target,        // the reviewed user (profile owner)
        UserMinimalDTO counterpart,   // the other participant
        MessageResponseDTO lastMessage,
        Long totalMessageCount,
        Instant lastMessageAt,
        boolean blocked               // either participant blocked the other
) {
}
