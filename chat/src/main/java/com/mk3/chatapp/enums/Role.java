package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public enum Role {
    SUPER_ADMIN(
            3,
            Arrays.stream(Permission.values())
                    .collect(java.util.stream.Collectors.toSet())
    ),
    ADMIN(
            2,
            Set.of(
                    Permission.MANAGE_ROLES,
                    Permission.VIEW_AUDIT_LOGS,
                    Permission.CHANGE_USERNAME,
                    Permission.BAN_USERS,
                    Permission.KICK_USERS,
                    Permission.MUTE_USERS,
                    Permission.DELETE_MESSAGES
            )
    ),
    MODERATOR(
            1,
            Set.of(
                    Permission.CHANGE_USERNAME,
                    Permission.BAN_USERS,
                    Permission.KICK_USERS,
                    Permission.MUTE_USERS,
                    Permission.DELETE_MESSAGES
            )
    ),
    USER(0, Collections.emptySet()),
    UNCLAIMED_USER(0, Collections.emptySet()),
    GUEST(0, Collections.emptySet());

    @Getter
    private final int level;

    @Getter
    private final Set<Permission> permissions;

    public List<SimpleGrantedAuthority> getGrantedAuthorities() {
        var authorities = getPermissions()
                .stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toList());

        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));

        return authorities;
    }

    public boolean canActOn(Role other) {
        return this.level > other.level;
    }

    public boolean isStaffMember() {
        return this.level > USER.level;
    }

    public boolean hasPermission(Permission permission) {
        return this.permissions.contains(permission);
    }
}
