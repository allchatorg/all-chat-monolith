package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UnclaimedRegisterDTO(
        @NotBlank(message = "Username is required") String username,
        @NotNull(message = "Age verification is required") boolean isOver18,
        @NotNull(message = "Digital consent age verification is required") boolean isOverDigitalConsent,
        @NotNull(message = "Terms and privacy acceptance is required") boolean acceptsTermsAndPrivacy,
        String captchaToken) {
}