package com.mk3.chatapp.dtos.responses;

import java.time.Instant;

// Broadcast on every room-promotion status transition (type
// ROOM_PROMOTION_UPDATE) to /topic/public-chat (the promoted list is global)
// and to the owner's user topic; status is a string for the same reason as
// PromotedMessageEventDTO
public record RoomPromotionEventDTO(
        Long chatRoomId,
        String chatRoomName,
        Long promotionId,
        String status,
        Long ownerId,
        Instant approvedAt
) {
}
