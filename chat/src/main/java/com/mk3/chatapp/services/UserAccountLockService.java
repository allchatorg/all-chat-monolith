package com.mk3.chatapp.services;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes membership and upload quota changes for one account. */
@Service
@RequiredArgsConstructor
public class UserAccountLockService {
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public User lock(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("An authenticated user is required");
        }

        // Joining also runs during registration and role changes. Persist their pending
        // changes before refreshing, and retain the row lock until the caller commits.
        entityManager.flush();
        User locked = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        return locked;
    }
}
