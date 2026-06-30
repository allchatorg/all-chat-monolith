package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.Role;

public record RoleUpdateRequest(
        Long userId,
        Role role
) {
}
