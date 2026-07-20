package com.example.adsportalbe.dto.promotion;

import com.example.adsportalbe.enums.CanceledBy;
import com.example.adsportalbe.enums.PromotedMessageStatus;

import java.time.Instant;

public record PromotedMessageDetailDto(
        Long id,
        Long messageId,
        String messageContent,
        String messageSenderUsername,
        Instant messageCreatedAt,
        boolean messageDeleted,
        Long chatRoomId,
        String chatRoomName,
        PromotedMessageStatus status,
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
