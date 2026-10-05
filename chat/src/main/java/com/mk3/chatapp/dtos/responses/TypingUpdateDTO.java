package com.mk3.chatapp.dtos.responses;

public record TypingUpdateDTO(Long chatRoomId, Long userId, String username, boolean typing, long expiresInMs) {
}
