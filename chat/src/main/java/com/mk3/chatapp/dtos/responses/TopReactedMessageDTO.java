package com.mk3.chatapp.dtos.responses;

public record TopReactedMessageDTO(
        Long messageId,
        Long roomId,
        Long reactionCount) {
}
