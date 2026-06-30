package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.Role;

import java.util.List;

public record UserSearchRequestDTO(
        List<Role> roles,
        String usernameOrId,
        Boolean over18,
        Boolean claimed,
        Boolean verified,
        Boolean banned,
        Integer page,
        Integer size,
        String sort
) {
}
