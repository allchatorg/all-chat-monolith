package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateChatRoomRequestDTO(
        @NotBlank(message = "Name cannot be empty")
        @Pattern(
                regexp = "^[A-Za-z0-9 ]+$",
                message = "Name must contain only letters, numbers, and spaces"
        )
        String name
) {
}
