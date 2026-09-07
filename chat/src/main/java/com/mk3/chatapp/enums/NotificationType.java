package com.mk3.chatapp.enums;

/**
 * Kinds of user-facing notifications. Adding a type here (plus a producer
 * calling NotificationService.createAndSend and a frontend registry entry)
 * is all a new notification requires.
 * Planned: SERVER_ANNOUNCEMENT, REPORT_CASE_RESOLVED.
 */
public enum NotificationType {
    WARNING,
    AD_SUBMITTED,
    AD_APPROVED,
    AD_REJECTED,
    AD_CANCELED,
    AD_COMPLETED,
    PROMOTION_SUBMITTED,
    PROMOTION_APPROVED,
    PROMOTION_DENIED,
    PROMOTION_CANCEL_REQUESTED,
    PROMOTION_CANCELED,
    ROOM_PROMOTION_SUBMITTED,
    ROOM_PROMOTION_APPROVED,
    ROOM_PROMOTION_DENIED,
    ROOM_PROMOTION_CANCEL_REQUESTED,
    ROOM_PROMOTION_CANCELED,
    MODERATOR_ACCEPTED,
    COMMUNITY_NIGHT_REMINDER
}
