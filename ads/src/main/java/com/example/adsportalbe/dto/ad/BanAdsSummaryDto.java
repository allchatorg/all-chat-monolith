package com.example.adsportalbe.dto.ad;

/**
 * Per-user ads summary shown in the staff ban form: purchase counts by status
 * plus the count and total of pending (SUBMITTED + AUTHORIZED) purchases that
 * would be refunded by a permanent ban.
 */
public record BanAdsSummaryDto(
        long totalAds,
        long submittedCount,
        long activeCount,
        long completedCount,
        long rejectedCount,
        long pendingRefundCount,
        double pendingRefundTotal,
        String currency
) {}
