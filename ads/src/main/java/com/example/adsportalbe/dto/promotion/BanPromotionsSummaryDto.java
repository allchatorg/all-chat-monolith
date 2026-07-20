package com.example.adsportalbe.dto.promotion;

/**
 * Per-user promoted-message summary shown in the staff ban form: counts by
 * status plus the totals a permanent ban would release (PENDING holds) and
 * refund (APPROVED captures).
 */
public record BanPromotionsSummaryDto(
        long totalPromotions,
        long pendingCount,
        long approvedCount,
        long deniedCount,
        long canceledCount,
        double pendingReleaseTotal,
        double approvedRefundTotal,
        String currency) {
}
