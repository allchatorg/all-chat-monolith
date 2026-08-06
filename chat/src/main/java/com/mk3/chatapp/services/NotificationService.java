package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.NotificationDTO;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Page;

public interface NotificationService {

    /**
     * Producer entry point: persists a notification for the recipient and,
     * after the transaction commits, pushes it over STOMP if they are online.
     * Any module (ads included — ads already depends on chat) creates
     * notifications exclusively through this method.
     *
     * @param metadata      opaque JSON string for type-specific detail rendering, nullable
     * @param referenceType optional domain-object pointer for future deep-link/fetch types, nullable
     */
    NotificationDTO createAndSend(User recipient, NotificationType type, String title,
                                  String body, String metadata, String referenceType, Long referenceId);

    Page<NotificationDTO> getMyNotifications(int page, int size);

    long getUnreadCount();

    NotificationDTO markRead(Long notificationId);

    NotificationDTO markUnread(Long notificationId);

    void deleteNotification(Long notificationId);

    int markAllRead();
}
