package com.example.adsportalbe.dto.promotion;

import com.example.adsportalbe.enums.PromotedMessageStatus;

public record PromotedMessageSearchRequestDto(
        PromotedMessageStatus status,
        int page,
        int size,
        String sort,
        Long userId,
        String email) {
}
