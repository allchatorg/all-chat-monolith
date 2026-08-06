package com.mk3.chatapp.listeners;

import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.events.NotificationCreatedEvent;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes a freshly persisted notification to its recipient only after the
 * creating transaction commits — clients react by touching the notification
 * (fetch, mark read), which would 404 against an uncommitted row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryListener {

    private final WebSocketBroadcastService webSocketBroadcastService;

    @TransactionalEventListener
    public void onNotificationCreated(NotificationCreatedEvent event) {
        webSocketBroadcastService.broadcastToUser(event.userId(), WebSocketMessage.builder()
                .type(WebSocketMessageType.NOTIFICATION)
                .data(event.notification())
                .build());
    }
}
