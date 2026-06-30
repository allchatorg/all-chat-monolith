package com.mk3.chatapp.dtos.responses;

import java.util.List;

public record ChatRoomWithMessageMetadataDTO(
        Long id,
        String name,
        List<MessageResponseDTO> messages,
        boolean isArchived,
        Long totalMessages,
        boolean hasPrevious,
        boolean hasNext,
        Long firstMessageId,
        Long lastMessageId,
        Long lastReadMessage
) {
}
