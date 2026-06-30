package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.UserChatRoomDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;

import java.util.List;

public interface UserChatRoomService {
    UserChatRoom joinChatRoom(User user, ChatRoom chatRoom);

    UserChatRoom findByUserAndChatRoom(User user, ChatRoom chatRoom);

    void deleteById(Long id);

    void leaveChatRoom(User user, ChatRoom chatRoom);

    List<UserChatRoom> findAllByUser(User user);

    List<UserChatRoom> findAllByUser(Role requesterRole, User user);

    List<UserChatRoomDTO> findAllByUserWithPopulation(User user);

    void updateLastReadMessage(User user, ChatRoom chatRoom, Message message);
}
