package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Permission {
    MANAGE_ROLES("manage_roles"),
    VIEW_AUDIT_LOGS("view_audit_logs"),
    CHANGE_USERNAME("change_username"),
    BAN_USERS("ban_users"),
    KICK_USERS("kick_users"),
    MUTE_USERS("mute_users"),
    DELETE_MESSAGES("delete_messages");

    @Getter
    private final String permission;
}
