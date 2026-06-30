package com.mk3.chatapp.dtos.responses;

import java.util.List;

public record MessagePageDTO(
        List<MessageResponseDTO> messages,
        boolean hasPrevious,
        boolean hasNext,
        Long firstMessageId,
        Long lastMessageId
) {
}
