package com.example.adsportalbe.dto.roompromotion;

/**
 * Per-user room-promotion summary shown in the staff ban form: counts by
 * status plus the totals a permanent ban would release (PENDING holds) and
 * the APPROVED captures (which are NOT refunded on ban, but shown for context).
 */
public record BanRoomPromotionsSummaryDto(
        long totalPromotions,
        long pendingCount,
        long approvedCount,
        long deniedCount,
        long canceledCount,
        double pendingReleaseTotal,
        double approvedRefundTotal,
        String currency) {
}
