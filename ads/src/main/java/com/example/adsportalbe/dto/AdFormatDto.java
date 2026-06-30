package com.example.adsportalbe.dto;

import com.example.adsportalbe.models.ad.AdFormatType;
import com.example.adsportalbe.models.ad.TextPricingTierRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdFormatDto {
    private Long id;
    private AdFormatType type;
    private String title;
    private String description;
    private Double pricePerMille;
    private Boolean recommended;
    private List<String> features;
    private List<TextPricingTierRule> pricingTiers;
}
