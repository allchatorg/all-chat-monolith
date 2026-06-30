package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClaimAccountRequestDTO(
                @NotBlank(message = "Email is required") @Size(max = 254, message = "Email must be less than 254 characters") String email,

                @NotBlank(message = "Password is required") @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters") @Pattern(regexp = "^[\\p{Print}&&[^\\s]]{8,128}$", message = "Password contains invalid characters") String password) {
}
