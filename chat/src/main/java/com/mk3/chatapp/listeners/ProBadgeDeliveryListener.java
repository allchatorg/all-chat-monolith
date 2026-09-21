package com.mk3.chatapp.listeners;

import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.events.ProBadgeChangedEvent;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProBadgeDeliveryListener {
    private final WebSocketBroadcastService broadcast;

    @TransactionalEventListener
    public void onBadgeChanged(ProBadgeChangedEvent event) {
        try {
            broadcast.broadcastToPublicChat(WebSocketMessage.builder()
                    .type(WebSocketMessageType.PRO_BADGE_UPDATED).data(event.badge()).build());
        } catch (Exception e) {
            // Commit already succeeded; reconnect lookup repairs a missed notification.
            log.warn("Could not broadcast Pro badge revision {} for user {}",
                    event.badge().proBadgeRevision(), event.badge().userId(), e);
        }
    }
}
