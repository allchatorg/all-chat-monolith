package com.mk3.chatapp.dtos;

import java.time.LocalDateTime;

public record ErrorDTO(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {
}