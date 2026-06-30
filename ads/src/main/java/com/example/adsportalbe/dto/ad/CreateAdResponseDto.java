package com.example.adsportalbe.dto.ad;

import com.example.adsportalbe.enums.AdStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Returned by POST /api/v1/ads-portal/ads so the frontend can render a real
 * purchase confirmation (the created ad + the payment receipt) instead of a
 * blind "success" toast. The payment is authorized at this point; capture
 * happens on admin approval, so the receipt status is typically AUTHORIZED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdResponseDto {
    private Long adId;
    private String title;
    private AdStatus status;
    private Instant submittedAt;
    private ReceiptDto receipt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptDto {
        private Long id;
        private Double amountPaid;
        private String currency;
        private String status; // AUTHORIZED / CAPTURED
        private String cardBrand;
        private String cardLast4;
    }
}
