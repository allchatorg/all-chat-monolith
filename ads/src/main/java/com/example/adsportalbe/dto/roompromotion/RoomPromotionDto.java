package com.example.adsportalbe.dto.roompromotion;

import com.example.adsportalbe.enums.RoomPromotionStatus;

import java.time.Instant;

public record RoomPromotionDto(
        Long id,
        Long chatRoomId,
        String chatRoomName,
        RoomPromotionStatus status,
        Double amount,
        String currency,
        Instant submittedAt,
        Instant approvedAt,
        String email,
        Long userId,
        boolean cancelRequested) {
}
