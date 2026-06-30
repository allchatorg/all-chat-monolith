package com.mk3.chatapp.dtos.responses;

public record MessagingAvailabilityDTO(
        boolean messagingBlocked,
        String disabledReason
) {
}
