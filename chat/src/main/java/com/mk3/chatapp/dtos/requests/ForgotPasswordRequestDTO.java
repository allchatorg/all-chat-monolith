package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.Size;


public record ForgotPasswordRequestDTO(
        @Size(max = 254, message = "Email must be less than 254 characters")
        String email,
        @Size(max = 32, message = "Phone number must be less than 32 characters")
        String phoneNumber
) {
}
