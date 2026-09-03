package com.example.adsportalbe.dto.roompromotion;

import com.example.adsportalbe.enums.CanceledBy;
import com.example.adsportalbe.enums.RoomPromotionStatus;

import java.time.Instant;

public record RoomPromotionDetailDto(
        Long id,
        Long chatRoomId,
        String chatRoomName,
        boolean chatRoomArchived,
        RoomPromotionStatus status,
        CanceledBy canceledBy,
        String reason,
        Double amount,
        String currency,
        Instant submittedAt,
        Instant approvedAt,
        Instant resolvedAt,
        String email,
        Long userId,
        String cardBrand,
        String cardLast4,
        String receiptStatus,
        boolean cancelRequested,
        String cancelRequestReason,
        Instant cancelRequestedAt) {
}
