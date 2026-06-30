package com.mk3.chatapp.listeners;

import com.mk3.chatapp.services.ChatRoomInteractionService;
import com.mk3.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserService userService;
    private final ChatRoomInteractionService chatRoomInteractionService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        var connectedUser = headerAccessor.getUser();
        if (connectedUser == null) {
            throw new IllegalStateException("User is null in WebSocket connection event");
        }

        var test = userService.getPrincipal(connectedUser);

        chatRoomInteractionService.connectUserToChatRooms(test);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        var connectedUser = headerAccessor.getUser();
        if (connectedUser == null) {
            throw new IllegalStateException("User is null in WebSocket connection event");
        }

        var user = userService.getPrincipal(connectedUser);
        chatRoomInteractionService.disconnectUserFromChatRooms(user);
    }
}
