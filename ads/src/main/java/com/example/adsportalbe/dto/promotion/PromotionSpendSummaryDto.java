package com.example.adsportalbe.dto.promotion;

/**
 * The current user's promotion spend: PENDING holds awaiting review and the
 * total actually captured from them (including approved-then-canceled, which
 * is not refunded).
 */
public record PromotionSpendSummaryDto(
        long pendingCount,
        double pendingHoldTotal,
        long approvedCount,
        double totalSpent,
        String currency) {
}
