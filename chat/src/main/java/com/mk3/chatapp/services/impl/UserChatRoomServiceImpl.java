package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.RoomPopulationDTO;
import com.mk3.chatapp.dtos.responses.UserChatRoomDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.ChatRoomService;
import com.mk3.chatapp.services.MessagesService;
import com.mk3.chatapp.services.RoomActivityService;
import com.mk3.chatapp.services.UserChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserChatRoomServiceImpl implements UserChatRoomService {

    private final UserChatRoomRepository userChatRoomRepository;
    private final ChatRoomService chatRoomService;
    private final RoomActivityService roomActivityService;

    private final MessageMapper messageMapper;
    private final MessagesService messagesService;

    @Override
    @Transactional
    public UserChatRoom joinChatRoom(User user, ChatRoom chatRoom) {
        chatRoomService.validateUserCanJoinChatRoom(user, chatRoom);

        UserChatRoom existingUserChatRoom = userChatRoomRepository.findUserChatRoomByUserAndChatRoom(user, chatRoom)
                .orElse(null);
        if (existingUserChatRoom != null) {
            return findByUserAndChatRoom(user, chatRoom);
        }
        UserChatRoom userChatRoom = UserChatRoom.builder()
                .user(user)
                .chatRoom(chatRoom)
                .build();

        userChatRoomRepository.save(userChatRoom);
        return userChatRoom;
    }

    @Override
    public UserChatRoom findByUserAndChatRoom(User user, ChatRoom chatRoom) {
        return userChatRoomRepository.findUserChatRoomByUserAndChatRoom(user, chatRoom).orElseThrow(
                () -> new RuntimeException("User not joined to chat room: " + chatRoom.getName()));
    }

    @Override
    public void deleteById(Long id) {
        var userChatRoom = userChatRoomRepository.findById(id);
        userChatRoom.ifPresentOrElse(userChatRoom1 -> {
            userChatRoom1.setDeleted(true);
            userChatRoomRepository.save(userChatRoom1);
        }, () -> {
            throw new RuntimeException("User chat room not found with id: " + id);
        });
    }

    @Override
    public void leaveChatRoom(User user, ChatRoom chatRoom) {
        var userChatRoom = findByUserAndChatRoom(user, chatRoom);
        userChatRoomRepository.delete(userChatRoom);
    }

    @Override
    @Transactional
    public List<UserChatRoom> findAllByUser(User user) {
        // Only public rooms: presence tracking and the public room list must never
        // include private chats. Private memberships are managed by PrivateChatService.
        List<UserChatRoom> userChatRooms =
                userChatRoomRepository.findByUserAndChatRoom_Type(user, ChatRoomType.PUBLIC);
        List<UserChatRoom> validUserChatRooms = new ArrayList<>();
        List<UserChatRoom> invalidUserChatRooms = new ArrayList<>();

        for (UserChatRoom userChatRoom : userChatRooms) {
            try {
                chatRoomService.validateUserCanJoinChatRoom(user, userChatRoom.getChatRoom());
                validUserChatRooms.add(userChatRoom);
            } catch (IllegalArgumentException exception) {
                invalidUserChatRooms.add(userChatRoom);
            }
        }

        if (!invalidUserChatRooms.isEmpty()) {
            userChatRoomRepository.deleteAll(invalidUserChatRooms);
        }

        return validUserChatRooms;
    }

    @Override
    public List<UserChatRoom> findAllByUser(Role requesterRole, User user) {
        List<UserChatRoom> userChatRooms = findAllByUser(user);

        if (!requesterRole.isStaffMember()) {
            return userChatRoomRepository.saveAll(updateDeletedLastReadMessages(userChatRooms));
        }

        return userChatRooms;
    }

    @Override
    public List<UserChatRoomDTO> findAllByUserWithPopulation(User user) {
        List<UserChatRoom> userChatRooms = findAllByUser(user.getRole(), user);
        return addRoomPopulation(userChatRooms);
    }

    @Transactional
    @Override
    public void updateLastReadMessage(User user, ChatRoom chatRoom, Message message) {
        UserChatRoom userChatRoom = findByUserAndChatRoom(user, chatRoom);

        if (userChatRoom.getLastReadMessage() != null && userChatRoom.getLastReadMessage().getId() >= message.getId()) {
            throw new RuntimeException("Cannot update last read message to an older message");
        }

        userChatRoom.setLastReadMessage(message);
        userChatRoomRepository.save(userChatRoom);
    }

    private List<UserChatRoom> updateDeletedLastReadMessages(List<UserChatRoom> userChatRooms) {
        return userChatRooms.stream()
                .peek(userChatRoom -> {
                    var previousLastReadMessage = userChatRoom.getLastReadMessage();
                    if (previousLastReadMessage != null && previousLastReadMessage.getDeleted()) {
                        Message lastReadMessage = messagesService.findNearestPreviousNotDeletedMessage(
                                previousLastReadMessage.getChatRoom().getId(),
                                previousLastReadMessage.getId());
                        userChatRoom.setLastReadMessage(lastReadMessage);
                    }
                })
                .toList();
    }

    private List<UserChatRoomDTO> addRoomPopulation(List<UserChatRoom> userChatRooms) {
        return userChatRooms.stream()
                .map(userChatRoom -> {
                    RoomPopulationDTO roomPopulation = roomActivityService
                            .getRoomPopulation(userChatRoom.getChatRoom().getId().toString());

                    var lastReadMessageDTO = userChatRoom.getLastReadMessage() != null
                            ? messageMapper.toMessageResponseDTO(userChatRoom.getLastReadMessage())
                            : null;

                    return new UserChatRoomDTO(
                            userChatRoom.getId(),
                            userChatRoom.getChatRoom().getName(),
                            userChatRoom.getChatRoom().getRequiredAccessLevel(),
                            userChatRoom.getChatRoom().getId(),
                            roomPopulation,
                            null,
                            lastReadMessageDTO,
                            null);
                })
                .toList();
    }

}
