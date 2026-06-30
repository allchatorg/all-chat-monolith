package com.mk3.chatapp.dtos.requests;

public record MessageSearchRequestDTO(
        Long chatRoomId,
        String senderUsername,
        String content,
        String attachmentName,
        int page,
        int size) {
}
