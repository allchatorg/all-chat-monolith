package com.example.adsportalbe.dto.ad;

import com.example.adsportalbe.models.ad.AdFormatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdRequestDto {
    private String title;
    private AdFormatType adType;
    private String text;
    private String imageUrl;
    private String videoUrl;
    private String stripeId; // The PaymentMethod ID e.g. "pm_..."
    private Integer viewsBought;
    private Double calculatedPrice;
    private String stripeAid; // Connected account ID if needed, though usually handled on backend config or
    // specific logic
}
