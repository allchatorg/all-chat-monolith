package com.example.adsportalbe.dto;

import com.example.adsportalbe.models.ad.AdFormatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedAd implements Serializable {
    private Long id;
    private String title;
    private AdFormatType format; // Or format title
    private String imageUrl;
    private String videoUrl;
    private String textContent;

    // Views tracking
    private Integer totalViewsBought;
    private Integer servedViews;
}
