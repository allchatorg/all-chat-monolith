package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.CreateChatRoomRequestDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.identity.User;

import java.util.List;
import java.util.Optional;

public interface ChatRoomService {

    ChatRoom createChatRoom(CreateChatRoomRequestDTO request, User connectedUser);

    ChatRoom findById(Long chatRoomId);

    ChatRoom findByName(String chatRoomName);

    List<ChatRoom> searchChatRoomsByName(String name, Role role);

    Optional<ChatRoom> findRandomJoinableChatRoom(Role role, Long userId);

    List<String> getAccessibleSecureUserChatRoomNames(Role role);

    List<String> getAllSpecialChatRoomNames();

    void createDefaultPublicChatRooms();

    void createStaffChatRooms();

    List<ChatRoom> getRoleAccessibleUserChatRooms(Role role);

    void archiveChatRoom(Long chatRoomId);

    void unarchiveChatRoom(Long chatRoomId);

    void validateRoomIsNotArchived(ChatRoom chatRoom, String action);

    void validateUserCanJoinChatRoom(User user, ChatRoom chatRoom);
}
