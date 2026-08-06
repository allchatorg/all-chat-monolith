package com.mk3.chatapp.enums;

/**
 * Kinds of user-facing notifications. Adding a type here (plus a producer
 * calling NotificationService.createAndSend and a frontend registry entry)
 * is all a new notification requires.
 * Planned: SERVER_ANNOUNCEMENT, REPORT_CASE_RESOLVED, AD_UPDATE.
 */
public enum NotificationType {
    WARNING
}
