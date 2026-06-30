package com.example.adsportalbe.models.ad;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextPricingTierRule {
    private Integer maxCharacters;
    private Double pricePerMille;
}
