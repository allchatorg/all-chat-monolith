package com.example.adsportalbe.dto.ad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdViewsSummaryDto {
    private Long todaysViews;
    private Long yesterdaysViews;
    private Integer totalViewsBought;
    private Integer totalServedViews;
    private Long totalClicks;
    // Fraction (0.0-1.0), computed at read time as totalClicks / totalServedViews
    private Double overallCtr;
}
