package com.mk3.chatapp.dtos.responses;

import java.util.List;

public record ChatRoomDTO(
        Long id,
        String name,
        List<MessageResponseDTO> messages,
        boolean isArchived
) {
}
