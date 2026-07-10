package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ChatRoomRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.ChatRoomService;
import com.mk3.chatapp.services.UserCreationService;
import com.mk3.chatapp.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCreationServiceImpl implements UserCreationService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatRoomService chatRoomService;

    @Override
    @Transactional
    public void createUsersAndChatrooms() {
        User john = User.builder()
                .username("john_doe")
                .password(passwordEncoder.encode("Makedonija12!"))
                .email("john@example.com")
                .phoneNumber("+385915550142")
                .phoneNumberVerificationDate(Instant.now())
                .over18(true)
                .overDigitalConsent(true)
                .acceptsTermsAndPrivacy(true)
                .acceptsPolicies(true)
                .claimed(true)
                .verified(true)
                .emailVerified(true)
                .totalUploadUsage(0L)
                .displayColor(Utils.generateRandomHexColor())
                .role(Role.SUPER_ADMIN)
                .build();
        john = userRepository.save(john);

        // Create second user
        User jane = User.builder()
                .username("jane_doe")
                .password(passwordEncoder.encode("Makedonija12!"))
                .email("jane@example.com")
                .over18(true)
                .claimed(true)
                .verified(true)
                .totalUploadUsage(0L)
                .displayColor(Utils.generateRandomHexColor())
                .role(Role.ADMIN)
                .build();
        jane = userRepository.save(jane);

        // Create third user
        User alice = User.builder()
                .username("alice_smith")
                .password(passwordEncoder.encode("Makedonija12!"))
                .email("alice@example.com")
                .over18(true)
                .claimed(true)
                .verified(true)
                .emailVerified(true)
                .totalUploadUsage(0L)
                .displayColor(Utils.generateRandomHexColor())
                .role(Role.USER)
                .build();
        alice = userRepository.save(alice);

        // Create first chat room
        ChatRoom homeRoom = ChatRoom.builder()
                .name("Home")
                .build();
        homeRoom = chatRoomRepository.save(homeRoom);

        // Link users to first chat room
        userChatRoomRepository.save(UserChatRoom.builder().user(john).chatRoom(homeRoom).build());
        joinUserToStaffChatRooms(john);

        userChatRoomRepository.save(UserChatRoom.builder().user(jane).chatRoom(homeRoom).build());
        joinUserToStaffChatRooms(jane);

        userChatRoomRepository.save(UserChatRoom.builder().user(alice).chatRoom(homeRoom).build());
        joinUserToStaffChatRooms(alice);

        // Create second chat room
        ChatRoom randomRoom = ChatRoom.builder()
                .name("Random")
                .build();
        randomRoom = chatRoomRepository.save(randomRoom);

        // Link users to second chat room
        userChatRoomRepository.save(UserChatRoom.builder().user(john).chatRoom(randomRoom).build());
        userChatRoomRepository.save(UserChatRoom.builder().user(jane).chatRoom(randomRoom).build());
        userChatRoomRepository.save(UserChatRoom.builder().user(alice).chatRoom(randomRoom).build());
    }

    @Override
    @Transactional
    public void joinUserToStaffChatRooms(User user) {
        List<ChatRoom> staffRooms = chatRoomService.getRoleAccessibleUserChatRooms(user.getRole());

        for (ChatRoom room : staffRooms) {
            boolean alreadyJoined = userChatRoomRepository.existsByUserAndChatRoom(user, room);
            if (!alreadyJoined) {
                userChatRoomRepository.save(
                        UserChatRoom.builder()
                                .user(user)
                                .chatRoom(room)
                                .build());
            }
        }
    }

    @Override
    @Transactional
    public List<User> createTestUsers(int count) {
        List<User> users = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            User user = User.builder()
                    .username("test_user_" + i)
                    .password(passwordEncoder.encode("TestPassword123!"))
                    .email("testuser" + i + "@example.com")
                    .over18(true)
                    .claimed(true)
                    .verified(true)
                    .totalUploadUsage(0L)
                    .displayColor(Utils.generateRandomHexColor())
                    .role(Role.USER)
                    .build();
            users.add(userRepository.save(user));
        }

        log.info("✅ Created {} test users", count);
        return users;
    }
}
