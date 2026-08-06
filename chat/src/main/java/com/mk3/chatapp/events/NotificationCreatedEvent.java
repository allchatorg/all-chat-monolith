package com.mk3.chatapp.events;

import com.mk3.chatapp.dtos.responses.NotificationDTO;

public record NotificationCreatedEvent(Long userId, NotificationDTO notification) {
}
