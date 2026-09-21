package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    Optional<Reaction> findByMessageIdAndEmoji(Long messageId, String emoji);

}
