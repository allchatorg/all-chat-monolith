package com.example.adsportalbe.dto.promotion;

/**
 * Platform-wide promoted-message revenue for the admin dashboard: captured
 * revenue today/yesterday/all-time (CAPTURED receipts only — refunds excluded)
 * plus the PENDING authorized holds not yet captured.
 */
public record PromotedRevenueSummaryDto(
        double todayRevenue,
        double yesterdayRevenue,
        double totalRevenue,
        long pendingCount,
        double pendingHoldTotal,
        long approvedCount,
        String currency) {
}
