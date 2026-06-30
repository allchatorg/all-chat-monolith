package com.mk3.chatapp.services;

import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;

import java.util.List;

public interface WebSocketBroadcastService {

    void broadcastToChatRoom(String roomName, WebSocketMessage webSocketMessage);

    void broadcastToPublicChat(WebSocketMessage webSocketMessage);

    void broadcastToUser(Long userId, WebSocketMessage webSocketMessage);

    void broadcastToUsers(List<User> users,
                          WebSocketMessage webSocketMessage);

    void sendPrivateToUser(Long userId, WebSocketMessage webSocketMessage);
}
