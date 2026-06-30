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
}
