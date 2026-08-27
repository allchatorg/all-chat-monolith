package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotBlank;

public record UpdateTimeZoneRequest(
        @NotBlank String timeZone
) {
}
