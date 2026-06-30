package com.example.adsportalbe.dto.ad;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdRejectionRequestDto {

    @NotNull(message = "Ad ID is required")
    private Long adId;

    @NotNull(message = "Rejection reason is required")
    private String rejectionReason;
}
