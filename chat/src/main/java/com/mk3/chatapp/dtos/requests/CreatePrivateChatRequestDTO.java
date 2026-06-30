package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotNull;

public record CreatePrivateChatRequestDTO(
        @NotNull(message = "otherUserId cannot be null")
        Long otherUserId
) {
}
