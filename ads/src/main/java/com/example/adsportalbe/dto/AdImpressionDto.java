package com.example.adsportalbe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdImpressionDto {
    private Long adId;
    private Instant timestamp;
    private String ipAddress;
    private Long userId;
}
