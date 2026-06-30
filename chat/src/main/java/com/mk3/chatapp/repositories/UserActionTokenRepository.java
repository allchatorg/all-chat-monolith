package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.UserActionToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserActionTokenRepository extends JpaRepository<UserActionToken, Long> {

    Optional<UserActionToken> findByToken(String token);
}
