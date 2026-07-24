package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.identity.User;
import lombok.NonNull;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findById(Long id);

    @NonNull
    List<User> findAll(Specification<User> spec);

    @Query("SELECT u FROM User u WHERE u.claimed = false AND u.lastSeen < :cutoffTime AND u.role IN ('GUEST', 'UNCLAIMED_USER')")
    List<User> findStaleGuestAndUnclaimedUsers(@Param("cutoffTime") Instant cutoffTime);

    boolean existsByPhoneNumber(String phoneNumber);

    List<User> findByRoleIn(java.util.Collection<com.mk3.chatapp.enums.Role> roles);

    Optional<User> findByIdVerificationSessionId(String idVerificationSessionId);

}
