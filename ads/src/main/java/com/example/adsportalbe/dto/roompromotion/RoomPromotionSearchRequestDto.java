package com.example.adsportalbe.dto.roompromotion;

import com.example.adsportalbe.enums.RoomPromotionStatus;

public record RoomPromotionSearchRequestDto(
        RoomPromotionStatus status,
        int page,
        int size,
        String sort,
        Long userId,
        String email,
        Long chatRoomId) {
}
