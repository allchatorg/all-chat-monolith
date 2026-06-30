package com.example.adsportalbe.dto.ad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RevenueDto {
    private Double todayRevenue;
    private Double yesterdayRevenue;
}
