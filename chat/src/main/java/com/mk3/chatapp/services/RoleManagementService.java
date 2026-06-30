package com.mk3.chatapp.services;

import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.identity.User;

public interface RoleManagementService {
    void updateUserRole(User user, Role role);

}
