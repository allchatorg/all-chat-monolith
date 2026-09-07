package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.CreateChatRoomRequestDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.exceptions.ConflictException;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ChatRoomRepository;
import com.mk3.chatapp.services.ChatRoomService;
import com.mk3.chatapp.services.RoomActivityService;
import com.mk3.chatapp.utils.Constants;
import com.mk3.chatapp.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomActivityService roomActivityService;

    @Override
    public ChatRoom createChatRoom(CreateChatRoomRequestDTO request, User connectedUser) {

        String normalizedRequestName = request.name().toLowerCase().trim().replaceAll("\\s+", " ");

        chatRoomRepository.findByNameIgnoreCase(normalizedRequestName).ifPresent(existingChatRoom -> {
            if (existingChatRoom.isArchived()) {
                if (!connectedUser.getRole().isStaffMember()) {
                    throw new ConflictException(
                            "Sorry, creating a chat room with that name is not possible.");
                }

                throw new ConflictException(
                        "Chat room with name '" + request.name() + "' is archived and its name cannot be reused.");
            }

            throw new ConflictException("Chat room with name '" + request.name() + "' already exists.");
        });

        String titleCase = Utils.toTitleCase(normalizedRequestName);

        var chatRoom = ChatRoom.builder()
                .name(titleCase)
                .build();

        chatRoomRepository.save(chatRoom);
        roomActivityService.storeRoomMetadata(chatRoom.getId().toString(), chatRoom.getName());
        return chatRoom;
    }

    @Override
    public ChatRoom findById(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found with id: " + chatRoomId));
    }

    public ChatRoom findByName(String chatRoomName) {
        return chatRoomRepository.findByName((chatRoomName)).orElseThrow(
                () -> new RuntimeException("Chat room not found with name: " + chatRoomName)
        );
    }

    @Override
    public List<ChatRoom> searchChatRoomsByName(String name, Role role) {
        if (name == null || name.isBlank()) {
            return List.of();
        }

        String query = name.trim().toLowerCase(Locale.ROOT);
        List<ChatRoom> rooms;
        if (role != null && role.isStaffMember()) {
            rooms = chatRoomRepository.findByNameContainingIgnoreCase(query).stream()
                    .filter(chatRoom -> chatRoom.getRequiredAccessLevel().getLevel() <= role.getLevel())
                    .toList();
        } else {
            rooms = chatRoomRepository.findVisibleGuestChatRoomsByName(Role.GUEST, query);
        }

        // Rank exact matches before prefixes and other substring matches.
        return rooms.stream()
                .sorted(Comparator.comparingInt((ChatRoom room) -> {
                    String roomName = room.getName().trim().toLowerCase(Locale.ROOT);
                    if (roomName.equals(query)) {
                        return 0;
                    }
                    return roomName.startsWith(query) ? 1 : 2;
                })
                        .thenComparingInt(room -> room.getName().trim().length())
                        .thenComparing(room -> room.getName().trim(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ChatRoom::getId))
                .toList();
    }

    @Override
    public Optional<ChatRoom> findRandomJoinableChatRoom(Role role, Long userId) {
        Role requesterRole = role != null ? role : Role.GUEST;
        List<Role> accessibleRoles = Arrays.stream(Role.values())
                .filter(candidateRole -> candidateRole.getLevel() <= requesterRole.getLevel())
                .toList();

        long unjoinedRoomCount = chatRoomRepository.countUnjoinedJoinableChatRooms(accessibleRoles, userId);
        if (unjoinedRoomCount > 0) {
            int randomOffset = ThreadLocalRandom.current().nextInt(Math.toIntExact(unjoinedRoomCount));
            return chatRoomRepository.findUnjoinedJoinableChatRooms(accessibleRoles, userId,
                            PageRequest.of(randomOffset, 1))
                    .stream()
                    .findFirst();
        }

        long roomCount = chatRoomRepository.countJoinableChatRooms(accessibleRoles);
        if (roomCount == 0) {
            return Optional.empty();
        }

        int randomOffset = ThreadLocalRandom.current().nextInt(Math.toIntExact(roomCount));
        return chatRoomRepository.findJoinableChatRooms(accessibleRoles, PageRequest.of(randomOffset, 1))
                .stream()
                .findFirst();
    }

    @Override
    public List<String> getAccessibleSecureUserChatRoomNames(Role role) {
        List<String> accessibleRooms = new ArrayList<>();

        if (role == null) {
            return accessibleRooms;
        }

        if (role.canActOn(Role.ADMIN)) {
            accessibleRooms.add(Constants.SUPER_ADMINS_CHATROOM_NAME);
        }

        if (role.canActOn(Role.MODERATOR)) {
            accessibleRooms.add(Constants.ADMINS_CHATROOM_NAME);
        }

        if (role.isStaffMember()) {
            accessibleRooms.add(Constants.MODERATORS_CHATROOM_NAME);
        }

        return accessibleRooms;
    }

    @Override
    public List<String> getAllSpecialChatRoomNames() {
        return List.of(Constants.SUPER_ADMINS_CHATROOM_NAME, Constants.ADMINS_CHATROOM_NAME, Constants.MODERATORS_CHATROOM_NAME);
    }

    @Override
    public void createDefaultPublicChatRooms() {
        createDefaultPublicChatRoom(Constants.DEFAULT_CHATROOM_NAME);
        createDefaultPublicChatRoom(Constants.BUG_REPORTS_CHATROOM_NAME);
    }

    private ChatRoom createDefaultPublicChatRoom(String roomName) {
        var chatRoom = chatRoomRepository.findByNameIgnoreCase(roomName)
                .map(existingChatRoom -> {
                    if (!existingChatRoom.getName().equals(roomName)) {
                        existingChatRoom.setName(roomName);
                        return chatRoomRepository.save(existingChatRoom);
                    }

                    return existingChatRoom;
                })
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.builder()
                        .name(roomName)
                        .build()));

        roomActivityService.storeRoomMetadata(chatRoom.getId().toString(), chatRoom.getName());
        return chatRoom;
    }

    @Override
    public void createStaffChatRooms() {
        if (!chatRoomRepository.existsChatRoomByNameIgnoreCase(Constants.SUPER_ADMINS_CHATROOM_NAME)) {
            var superAdminRoom = ChatRoom.builder()
                    .name(Constants.SUPER_ADMINS_CHATROOM_NAME)
                    .requiredAccessLevel(Role.SUPER_ADMIN)
                    .build();
            chatRoomRepository.save(superAdminRoom);
            roomActivityService.storeRoomMetadata(superAdminRoom.getId().toString(), superAdminRoom.getName());
        }

        if (!chatRoomRepository.existsChatRoomByNameIgnoreCase(Constants.ADMINS_CHATROOM_NAME)) {
            var adminRoom = ChatRoom.builder()
                    .name(Constants.ADMINS_CHATROOM_NAME)
                    .requiredAccessLevel(Role.ADMIN)
                    .build();
            chatRoomRepository.save(adminRoom);
            roomActivityService.storeRoomMetadata(adminRoom.getId().toString(), adminRoom.getName());
        }

        if (!chatRoomRepository.existsChatRoomByNameIgnoreCase(Constants.MODERATORS_CHATROOM_NAME)) {
            var moderatorRoom = ChatRoom.builder()
                    .name(Constants.MODERATORS_CHATROOM_NAME)
                    .requiredAccessLevel(Role.MODERATOR)
                    .build();
            chatRoomRepository.save(moderatorRoom);
            roomActivityService.storeRoomMetadata(moderatorRoom.getId().toString(), moderatorRoom.getName());
        }
    }

    @Override
    public List<ChatRoom> getRoleAccessibleUserChatRooms(Role role) {
        return getAccessibleSecureUserChatRoomNames(role)
                .stream()
                .map(chatRoomName -> chatRoomRepository.findByName(chatRoomName)
                        .orElseThrow(() -> new RuntimeException("Chat room not found with name: " + chatRoomName))
                )
                .toList();
    }

    @Override
    public void archiveChatRoom(Long chatRoomId) {
        ChatRoom chatRoom = findById(chatRoomId);
        chatRoom.setArchived(true);
        chatRoomRepository.save(chatRoom);
    }

    @Override
    public void unarchiveChatRoom(Long chatRoomId) {
        ChatRoom chatRoom = findById(chatRoomId);
        chatRoom.setArchived(false);
        chatRoomRepository.save(chatRoom);
    }

    @Override
    public void validateRoomIsNotArchived(ChatRoom chatRoom, String action) {
        if (chatRoom == null) {
            throw new IllegalArgumentException("Chat room cannot be null");
        }

        if (chatRoom.isArchived()) {
            throw new IllegalArgumentException("Cannot " + action + " in archived chat room: " + chatRoom.getName());
        }
    }

    @Override
    public void validateUserCanJoinChatRoom(User user, ChatRoom chatRoom) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        if (chatRoom == null) {
            throw new IllegalArgumentException("Chat room cannot be null");
        }

        if (chatRoom.getRequiredAccessLevel().getLevel() > user.getRole().getLevel()) {
            throw new IllegalArgumentException(
                    "User does not have sufficient access level to join the chat room: " + chatRoom.getName());
        }

        if (chatRoom.isArchived() && !user.getRole().isStaffMember()) {
            throw new IllegalArgumentException(
                    "Archived chat rooms can only be joined by staff members: " + chatRoom.getName());
        }
    }
}
