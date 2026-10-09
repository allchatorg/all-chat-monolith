package com.example.adsportalbe.dto.ad;

import com.mk3.chatapp.vip.VipStatisticsResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyRevenueResponseDto {
    private List<MonthlyRevenueDto> data;
    private VipStatisticsResponse.Synchronization subscriptionSynchronization;
}
