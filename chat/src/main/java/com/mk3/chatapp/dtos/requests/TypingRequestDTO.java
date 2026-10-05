package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TypingRequestDTO(@NotNull @Positive Long chatRoomId, @NotNull Boolean typing) {
}
