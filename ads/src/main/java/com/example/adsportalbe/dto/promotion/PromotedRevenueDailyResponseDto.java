package com.example.adsportalbe.dto.promotion;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily captured promoted-message revenue for the dedicated admin chart.
 * Days with no captures are omitted; totalRevenue is the sum over the range.
 */
public record PromotedRevenueDailyResponseDto(List<DailyRevenue> dailyRevenue, double totalRevenue) {

    public record DailyRevenue(LocalDate date, double revenue) {
    }
}
