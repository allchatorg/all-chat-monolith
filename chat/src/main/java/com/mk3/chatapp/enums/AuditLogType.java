package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AuditLogType {
    BAN("BAN"),
    CHANGE_USERNAME("CHANGE_USERNAME"),
    MESSAGE_DELETE("MESSAGE_DELETE"),
    REVOKE_BAN("REVOKE_BAN"),
    WARNING("WARNING"),
    RESOLVE_CASE("RESOLVE_CASE"),
    PROMOTE_ROLE("PROMOTE_ROLE"),
    DEMOTE_ROLE("DEMOTE_ROLE"),
    NCMEC_REPORT("NCMEC_REPORT"),
    ARCHIVE_CHATROOM("ARCHIVE_CHATROOM"),
    UNARCHIVE_CHATROOM("UNARCHIVE_CHATROOM");

    @Getter
    private final String auditLogType;
}
