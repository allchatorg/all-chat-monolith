package com.mk3.chatapp.dtos.responses;

// Broadcast to the room on every promotion status transition (type
// PROMOTED_MESSAGE_UPDATE); status is a string for the same reason as
// PromotionInfoDTO
public record PromotedMessageEventDTO(
        Long messageId,
        Long chatRoomId,
        String chatRoomName,
        Long promotionId,
        String status,
        Long ownerId
) {
}
