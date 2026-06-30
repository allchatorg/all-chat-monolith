package com.example.adsportalbe.dto.requests;

public record UserSearchRequestDto(
        Integer page,
        Integer size,
        String sort,
        Long userId,
        String email) {
}
