package com.example.adsportalbe.dto.promotion;

import com.example.adsportalbe.enums.PromotedMessageStatus;

import java.time.Instant;

public record PromotedMessageDto(
        Long id,
        Long messageId,
        String messageContent,
        Long chatRoomId,
        String chatRoomName,
        PromotedMessageStatus status,
        Double amount,
        String currency,
        Instant submittedAt,
        String email,
        Long userId,
        boolean cancelRequested) {
}
