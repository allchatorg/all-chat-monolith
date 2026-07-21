package com.example.adsportalbe.dto.ad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdDailyStatsResponseDto {
    private Long adId;
    private Integer viewsBought;
    private Integer servedViews;
    private Long todaysViews;
    private Long yesterdaysViews;
    // Lifetime click count, independent of the fromDate filter (like servedViews)
    private Long totalClicks;
    private Long todaysClicks;
    // Fraction (0.0-1.0), computed at read time as totalClicks / servedViews
    private Double overallCtr;
    private List<DailyStatDto> dailyStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStatDto {
        private LocalDate date;
        private Long viewsCount;
        private Long clicksCount;
        // Fraction (0.0-1.0), clicksCount / viewsCount for the day
        private Double ctr;
    }
}
