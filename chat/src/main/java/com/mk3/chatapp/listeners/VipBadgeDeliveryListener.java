package com.mk3.chatapp.listeners;

import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.events.VipBadgeChangedEvent;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class VipBadgeDeliveryListener {
    private final WebSocketBroadcastService broadcast;

    @TransactionalEventListener
    public void onBadgeChanged(VipBadgeChangedEvent event) {
        try {
            broadcast.broadcastToPublicChat(WebSocketMessage.builder()
                    .type(WebSocketMessageType.VIP_BADGE_UPDATED).data(event.badge()).build());
        } catch (Exception e) {
            // Commit already succeeded; reconnect lookup repairs a missed notification.
            log.warn("Could not broadcast VIP badge revision {} for user {}",
                    event.badge().vipBadgeRevision(), event.badge().userId(), e);
        }
    }
}
