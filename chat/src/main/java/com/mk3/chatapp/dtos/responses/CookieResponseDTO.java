package com.mk3.chatapp.dtos.responses;

import org.springframework.http.ResponseCookie;

public record CookieResponseDTO(
        ResponseCookie accessTokenCookie,
        ResponseCookie refreshTokenCookie
) {
}
