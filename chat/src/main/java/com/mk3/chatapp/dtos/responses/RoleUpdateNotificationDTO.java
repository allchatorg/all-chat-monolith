package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.Role;

public record RoleUpdateNotificationDTO(boolean isPromotion, Role role) {
}
