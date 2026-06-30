package com.mk3.chatapp.dtos;

public record TagDTO(
        Long id,
        String name,
        boolean restrictedToAdults
) {
}