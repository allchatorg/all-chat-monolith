package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotNull;

public record UpdateMarketingPreferencesDTO(
        @NotNull(message = "Marketing preference is required")
        Boolean subscribedToMarketingEmails
) {
}
