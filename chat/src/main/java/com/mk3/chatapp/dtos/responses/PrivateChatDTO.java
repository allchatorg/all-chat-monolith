package com.mk3.chatapp.dtos.responses;

public record PrivateChatDTO(
        Long id,
        UserMinimalDTO counterpart,
        Integer unreadMessagesCount,
        MessageResponseDTO lastReadMessage,
        MessageResponseDTO lastMessage,
        boolean blocked
) {
}
