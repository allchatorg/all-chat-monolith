package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.RoleUpdateNotificationDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleManagementServiceImpl implements RoleManagementService {
    private final SecurityService securityService;
    private final UserService userService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final AuditLogService auditLogService;
    private final UserChatRoomService userChatRoomService;
    private final ChatRoomService chatRoomService;
    private final RoomActivityService roomActivityService;

    @Transactional
    @Override
    public void updateUserRole(User user, Role role) {
        var admin = securityService.getCurrentUser();
        validateRoleUpdate(user, role, admin);
        boolean isPromotion = user.getRole().getLevel() < role.getLevel();
        var previousRole = user.getRole();
        var previousUserChatRooms = userChatRoomService.findAllByUser(user);

        user.setRole(role);
        userService.save(user);

        // Broadcast role update notification to target user
        RoleUpdateNotificationDTO roleUpdateNotificationDTO = new RoleUpdateNotificationDTO(isPromotion, role);
        webSocketBroadcastService.broadcastToUser(user.getId(), new WebSocketMessage(
                WebSocketMessageType.ROLE_UPDATE_NOTIFICATION,
                null,
                roleUpdateNotificationDTO
        ));

        syncUserChatRooms(role, user, previousUserChatRooms);

        // Audit log for role change
        if (isPromotion) {
            auditLogService.logPromoteRole("PROMOTE_ROLE", null, user.getId(), previousRole, role);

        } else {
            auditLogService.logDemoteRole("DEMOTE_ROLE", null, user.getId(), previousRole, role);
        }
    }

    private void validateRoleUpdate(User user, Role role, User admin) {
        if (!admin.getRole().canActOn(user.getRole())) {
            throw new IllegalStateException("You cannot change the role of this user");
        }

        if (admin.getRole().getLevel() < role.getLevel()) {
            throw new IllegalStateException("You cannot assign a role with a higher level than your own");
        }

        if (user.getRole() == role) {
            throw new IllegalStateException("You cannot assign the same role to this user");
        }

        if (user.getRole() == Role.UNCLAIMED_USER || user.getRole() == Role.GUEST) {
            throw new IllegalStateException("You cannot make role changes to unclaimed users or guests. Only registered users can have their roles changed.");
        }
    }

    @Transactional
    public void syncUserChatRooms(Role newRole, User user, List<UserChatRoom> userChatRooms) {

        var roleAccessibleRooms = chatRoomService.getRoleAccessibleUserChatRooms(newRole);

        roleAccessibleRooms.stream()
                .filter(room -> userChatRooms.stream()
                        .noneMatch(userRoom -> userRoom.getChatRoom().getId().equals(room.getId())))
                .forEach(room -> userChatRoomService.joinChatRoom(user, room));

        userChatRooms.stream()
                .filter(room -> {
                    var required = room.getChatRoom().getRequiredAccessLevel();
                    return required != null && required.getLevel() > newRole.getLevel();
                })
                .forEach(room -> {
                    roomActivityService.userLeftRoom(
                            room.getChatRoom().getId().toString(),
                            user.getId().toString()
                    );
                    userChatRoomService.leaveChatRoom(user, room.getChatRoom());
                });
    }
}
