package com.mk3.chatapp.services;

import com.mk3.chatapp.models.identity.User;

import java.util.List;

public interface UserCreationService {
    void createUsersAndChatrooms();

    List<User> createTestUsers(int count);

    void joinUserToStaffChatRooms(User user);
}
