package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BanAppealRequestDTO(
        @NotBlank
        @Size(min = 50, max = 2000, message = "Appeal text must be between 50 and 2000 characters")
        String appealText,

        @Size(max = 1000, message = "This field cannot exceed 1000 characters")
        String whatWillChange
) {
}
