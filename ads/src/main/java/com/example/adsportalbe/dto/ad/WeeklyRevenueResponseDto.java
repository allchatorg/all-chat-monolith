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
public class WeeklyRevenueResponseDto {
    private List<WeeklyRevenueDto> data;
    private VipStatisticsResponse.Synchronization subscriptionSynchronization;
}
