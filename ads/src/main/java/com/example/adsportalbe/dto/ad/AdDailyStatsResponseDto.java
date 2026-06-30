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
    private List<DailyStatDto> dailyStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStatDto {
        private LocalDate date;
        private Long viewsCount;
    }
}
