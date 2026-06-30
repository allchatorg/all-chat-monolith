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
public class UserAdViewsDailyBreakdownDto {
    private List<DailyViewStats> dailyViews;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyViewStats {
        private LocalDate date;
        private Long viewsCount;
    }
}
