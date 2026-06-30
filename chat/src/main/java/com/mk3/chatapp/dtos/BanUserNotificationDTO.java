package com.mk3.chatapp.dtos;

import com.mk3.chatapp.enums.BanType;

public record BanUserNotificationDTO(
        Long userId,
        String roomName,
        BanType banType,
        boolean deleteMessages,
        String deleteMessagesAfter
) {
}
