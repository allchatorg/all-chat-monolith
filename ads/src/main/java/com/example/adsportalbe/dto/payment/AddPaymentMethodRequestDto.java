package com.example.adsportalbe.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddPaymentMethodRequestDto {
    @NotBlank(message = "Payment Method ID is required")
    private String paymentMethodId;
}
