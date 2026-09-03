package com.example.adsportalbe.dto.ad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyRevenueDto {
    private String day;
    private Double revenue; // ad revenue only
    private Double promotedRevenue; // message promotions only
    private Double roomPromotedRevenue;
}
