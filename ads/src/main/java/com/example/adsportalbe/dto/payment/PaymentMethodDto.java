package com.example.adsportalbe.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodDto {
    private String id;
    private String brand;
    private String last4;
    private Long expMonth;
    private Long expYear;
    private String cardholderName;
}
