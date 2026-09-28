package com.example.adsportalbe.dto.ad;

import com.mk3.chatapp.pro.ProStatisticsResponse;
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
    private ProStatisticsResponse.Synchronization subscriptionSynchronization;
}
