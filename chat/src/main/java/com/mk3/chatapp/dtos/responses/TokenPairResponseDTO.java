package com.mk3.chatapp.dtos.responses;

public record TokenPairResponseDTO(
        String accessToken,
        String refreshToken
) {
}
