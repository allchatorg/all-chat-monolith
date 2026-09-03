package com.mk3.chatapp.enums;

/**
 * Kinds of user-facing notifications. Adding a type here (plus a producer
 * calling NotificationService.createAndSend and a frontend registry entry)
 * is all a new notification requires.
 * Planned: SERVER_ANNOUNCEMENT, REPORT_CASE_RESOLVED.
 */
public enum NotificationType {
    WARNING,
    AD_APPROVED,
    AD_REJECTED,
    AD_COMPLETED,
    PROMOTION_APPROVED,
    PROMOTION_DENIED,
    PROMOTION_CANCELED,
    ROOM_PROMOTION_APPROVED,
    ROOM_PROMOTION_DENIED,
    ROOM_PROMOTION_CANCELED,
    MODERATOR_ACCEPTED,
    COMMUNITY_NIGHT_REMINDER
}
