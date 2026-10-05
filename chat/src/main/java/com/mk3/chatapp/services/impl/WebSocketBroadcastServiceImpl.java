package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.dtos.responses.ChatRoomDTO;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.events.TypingAccessChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketBroadcastServiceImpl implements WebSocketBroadcastService {
    private static final String PUBLIC_CHAT_DESTINATION = "/topic/public-chat";
    private static final String CHAT_ROOM_DESTINATION = "/topic/chat-room.";
    private static final String USER_TOPIC_DESTINATION = "/topic/user.";
    private static final String PRIVATE_MESSAGES_QUEUE = "/queue/private-messages";
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ApplicationEventPublisher events;

    @Override
    public void broadcastToChatRoom(String roomName, WebSocketMessage payload) {
        if (payload.getType() == WebSocketMessageType.CHATROOM_ARCHIVED && payload.getData() instanceof ChatRoomDTO room) {
            events.publishEvent(new TypingAccessChangedEvent(null, room.id()));
        }
        String destination = CHAT_ROOM_DESTINATION + roomName;
        simpMessagingTemplate.convertAndSend(destination, payload);
    }

    @Override
    public void broadcastToPublicChat(WebSocketMessage webSocketMessage) {
        if (webSocketMessage.getType() == WebSocketMessageType.MESSAGE_SENDING_AVAILABILITY_UPDATE) {
            events.publishEvent(new TypingAccessChangedEvent(null, null));
        }
        simpMessagingTemplate.convertAndSend(PUBLIC_CHAT_DESTINATION, webSocketMessage);
    }

    @Override
    public void broadcastToUser(Long userId, WebSocketMessage webSocketMessage) {
        switch (webSocketMessage.getType()) {
            case BAN_USER, ROLE_UPDATE_NOTIFICATION, ID_VERIFICATION_REQUIRED, ID_VERIFICATION_RESULT ->
                    events.publishEvent(new TypingAccessChangedEvent(userId, null));
            default -> { }
        }
        String destination = USER_TOPIC_DESTINATION + userId;
        simpMessagingTemplate.convertAndSend(destination, webSocketMessage);
    }

    @Override
    public void broadcastToUsers(List<User> users, WebSocketMessage webSocketMessage) {
        for (var user : users) {
            broadcastToUser(user.getId(), webSocketMessage);
        }
    }

    @Override
    public void sendPrivateToUser(Long userId, WebSocketMessage webSocketMessage) {
        simpMessagingTemplate.convertAndSendToUser(userId.toString(), PRIVATE_MESSAGES_QUEUE, webSocketMessage);
    }
}
