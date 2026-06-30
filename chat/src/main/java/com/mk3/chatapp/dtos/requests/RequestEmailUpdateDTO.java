package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestEmailUpdateDTO(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New email is required")
        @Email(message = "Invalid email format")
        @Size(max = 254, message = "Email must be less than 254 characters")
        String newEmail
) {
}
