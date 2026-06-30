package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.Reaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    Optional<Reaction> findByMessageIdAndEmoji(Long messageId, String emoji);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reaction r WHERE r.message.id = :messageId AND r.emoji = :emoji")
    Optional<Reaction> findByMessageIdAndEmojiForUpdate(@Param("messageId") Long messageId,
            @Param("emoji") String emoji);
}