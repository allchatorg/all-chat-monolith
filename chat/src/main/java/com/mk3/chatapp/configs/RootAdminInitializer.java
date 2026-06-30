package com.mk3.chatapp.configs;

import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.ChatRoomService;
import com.mk3.chatapp.services.UserCreationService;
import com.mk3.chatapp.utils.RootAdminUsers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RootAdminInitializer {

    private final UserRepository userRepository;
    private final UserCreationService userCreationService;
    private final ChatRoomService chatRoomService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void promoteRootAdmins() {
        chatRoomService.createStaffChatRooms();

        Set<Long> handledUserIds = new HashSet<>();
        RootAdminUsers.usernames().forEach(username ->
                userRepository.findByUsernameIgnoreCase(username)
                        .ifPresent(user -> promoteRootAdmin(user, handledUserIds)));
        RootAdminUsers.emails().forEach(email ->
                userRepository.findByEmailIgnoreCase(email)
                        .ifPresent(user -> promoteRootAdmin(user, handledUserIds)));
    }

    private void promoteRootAdmin(User user, Set<Long> handledUserIds) {
        if (user.getId() != null && !handledUserIds.add(user.getId())) {
            return;
        }

        boolean changed = user.getRole() != Role.SUPER_ADMIN || !user.isVerified();
        user.setRole(Role.SUPER_ADMIN);
        user.setVerified(true);

        User savedUser = userRepository.save(user);
        userCreationService.joinUserToStaffChatRooms(savedUser);

        if (changed) {
            log.info("Promoted root admin account. userId={} username={}", savedUser.getId(),
                    savedUser.getApplicationUsername());
        }
    }
}
