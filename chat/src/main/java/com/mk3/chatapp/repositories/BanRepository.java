package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.Ban;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BanRepository extends JpaRepository<Ban, Long> {
    Optional<Ban> findByUserAndActiveIs(User user, boolean active);


    @Query("""
                SELECT b FROM Ban b
                WHERE b.active = true
                  AND (:username IS NULL OR b.user.username = :username)
                  AND (:userId IS NULL OR b.user.id = :userId)
            """)
    Page<Ban> findActiveBans(@org.springframework.lang.Nullable String username,
                             @org.springframework.lang.Nullable Long userId,
                             Pageable pageable);

    boolean existsByUserAndActiveIs(User user, boolean active);

    List<Ban> findByActiveIsTrueAndExpiresAtIsNotNullAndExpiresAtAfter(Instant instant);

    List<Ban> findByActiveIsTrueAndExpiresAtIsNotNullAndExpiresAtBefore(Instant now);
}