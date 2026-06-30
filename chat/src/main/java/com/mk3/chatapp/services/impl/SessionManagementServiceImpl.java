package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.models.LastSessionInfo;
import com.mk3.chatapp.repositories.LastSessionInfoRepository;
import com.mk3.chatapp.services.SessionManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static com.mk3.chatapp.utils.IpAddressUtils.getClientIpAddress;

@Service
@RequiredArgsConstructor
public class SessionManagementServiceImpl implements SessionManagementService {
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final LastSessionInfoRepository lastSessionInfoRepository;

    @Override
    public String establishAndLogAuthenticatedSession(Authentication authentication, HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        authentication.getName();

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        Long userId = Long.parseLong(authentication.getName());
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        updateLastSessionInfo(userId, session.getId(), ipAddress, userAgent);

        return session.getId();
    }

    @Override
    public Collection<? extends Session> findSessionsByUserId(Long userId) {
        Map<String, ? extends Session> userSessions = sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                userId.toString()
        );
        return userSessions.values();
    }

    @Override
    public void expireUserSessions(Long userId) {
        Collection<? extends Session> sessions = findSessionsByUserId(userId);

        sessions.forEach(session -> sessionRepository.deleteById(session.getId()));
    }

    @Override
    public void expireSessionById(String sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    @Override
    public Optional<LastSessionInfo> getLastSessionInfo(Long userId) {
        return lastSessionInfoRepository.findById(userId);
    }

    private void updateLastSessionInfo(Long userId, String sessionId, String ipAddress, String userAgent) {
        lastSessionInfoRepository.findById(userId)
                .ifPresentOrElse(
                        lastSessionInfo -> {
                            lastSessionInfo.setSessionId(sessionId);
                            lastSessionInfo.setIpAddress(ipAddress);
                            lastSessionInfo.setUserAgent(userAgent);
                            lastSessionInfoRepository.save(lastSessionInfo);
                        },
                        () -> {
                            LastSessionInfo newLastSessionInfo = LastSessionInfo.builder()
                                    .userId(userId)
                                    .sessionId(sessionId)
                                    .ipAddress(ipAddress)
                                    .userAgent(userAgent)
                                    .build();
                            lastSessionInfoRepository.save(newLastSessionInfo);
                        }
                );
    }


}
