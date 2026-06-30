package com.mk3.chatapp.security;

import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.services.SecurityService;
import com.mk3.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component("security")
@RequiredArgsConstructor
public class SecurityExpression {

    private final SecurityService securityService;
    private final UserService userService;

    public boolean canActOnTargetUser(Long targetUserId) {
        var currentUser = securityService.getCurrentUser();

        if (targetUserId == null) return false;

        var targetUser = userService.findById(targetUserId);

        if (targetUser.getId().equals(currentUser.getId())) {
            return true;
        }

        Role targetRole = targetUser.getRole();
        return currentUser.getRole().canActOn(targetRole);
    }

    public void assertCanActOn(Long targetUserId) {
        if (!canActOnTargetUser(targetUserId)) {
            throw new AccessDeniedException("You cannot act on this user");
        }
    }

    public boolean isAdmin() {
        var currentUser = securityService.getCurrentUser();
        if (currentUser == null) return false;
        return currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.SUPER_ADMIN;
    }

    public boolean isStaffMember() {
        var currentUser = securityService.getCurrentUser();
        return currentUser != null && currentUser.getRole().isStaffMember();
    }
}
