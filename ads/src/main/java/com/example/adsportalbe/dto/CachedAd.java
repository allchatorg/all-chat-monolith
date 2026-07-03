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
    // imageUrl/videoUrl hold bare storage object keys (e.g. "dev/uuid_file.png"),
    // resolved to real URLs at serve time via FileUploadService.getFileUrl.
    private String imageUrl;
    private String videoUrl;
    private String textContent;

    // Views tracking
    private Integer totalViewsBought;
    private Integer servedViews;
}
