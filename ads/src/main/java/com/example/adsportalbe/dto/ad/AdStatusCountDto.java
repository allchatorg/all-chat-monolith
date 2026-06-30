package com.example.adsportalbe.dto.ad;

import com.example.adsportalbe.enums.AdStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdStatusCountDto {
    private AdStatus status;
    private Long count;
}

