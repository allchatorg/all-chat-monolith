package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
                @NotBlank(message = "Current password is required") String currentPassword,

                @NotBlank(message = "New password is required") @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters") @Pattern(regexp = "^[\\p{Print}&&[^\\s]]{8,128}$", message = "Password contains invalid characters") String newPassword) {
}
