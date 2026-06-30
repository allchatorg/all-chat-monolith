package com.mk3.chatapp.services;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityService {
    private final UserRepository userRepository;

    String GUEST_USERNAME = "anonymousUser";

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || GUEST_USERNAME.equals(auth.getName())) {
            return null;
        }

        Long userId = Long.parseLong(auth.getName());
        return userRepository.findById(userId).orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("User not found")
        );
    }
}
