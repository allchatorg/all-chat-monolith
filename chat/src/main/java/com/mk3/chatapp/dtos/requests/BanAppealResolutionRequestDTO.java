package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.BanAppealDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BanAppealResolutionRequestDTO(
        @NotNull
        BanAppealDecision decision,

        @NotBlank
        @Size(min = 10, max = 2000, message = "Internal note must be between 10 and 2000 characters")
        String internalNote,

        @Size(max = 1000, message = "User-facing message cannot exceed 1000 characters")
        String userFacingMessage
) {
}
