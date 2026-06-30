package com.example.adsportalbe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServeAdRequestDto {
    private Long userId;
    private String ipAddress;
}
