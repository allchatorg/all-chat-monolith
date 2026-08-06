package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.NotificationType;

public record NotificationDTO(
        Long id,
        NotificationType type,
        String title,
        String body,
        String metadata,
        String referenceType,
        Long referenceId,
        String readAt,
        String createdAt
) {
}
