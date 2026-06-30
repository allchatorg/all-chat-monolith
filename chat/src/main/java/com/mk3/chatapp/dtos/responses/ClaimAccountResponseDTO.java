package com.mk3.chatapp.dtos.responses;

public record ClaimAccountResponseDTO(
        UserDTO user,
        SessionTokenDTO sessionToken
) {
}
