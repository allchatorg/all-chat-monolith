package com.mk3.chatapp.services;

import com.mk3.chatapp.models.LastSessionInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.session.Session;

import java.util.Collection;
import java.util.Optional;

public interface SessionManagementService {

    String establishAndLogAuthenticatedSession(Authentication authentication, HttpServletRequest request);

    Collection<? extends Session> findSessionsByUserId(Long userId);

    void expireUserSessions(Long userId);

    void expireSessionById(String sessionId);

    Optional<LastSessionInfo> getLastSessionInfo(Long userId);
}
