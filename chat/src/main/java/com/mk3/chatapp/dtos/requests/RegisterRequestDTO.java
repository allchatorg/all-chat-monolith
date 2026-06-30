package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "Username is required") @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters") String username,

        @NotBlank(message = "Email is required") @Size(max = 254, message = "Email must be less than 254 characters") String email,

        String phoneNumber,

        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters") @Pattern(regexp = "^[\\p{Print}&&[^\\s]]{8,128}$", message = "Password contains invalid characters") String password,

        @NotNull(message = "Age verification is required") boolean isOver18,

        @NotNull(message = "Digital consent age verification is required") boolean isOverDigitalConsent,

        @NotNull(message = "Terms and privacy acceptance is required") boolean acceptsTermsAndPrivacy,

        String captchaToken) {
}
