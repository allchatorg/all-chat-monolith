package com.mk3.chatapp.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.dtos.ErrorDTO;
import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.enums.RequiredVerificationEnum;
import com.mk3.chatapp.mappers.BanMapper;
import com.mk3.chatapp.models.Ban;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.BanCacheService;
import com.mk3.chatapp.services.IpService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.mk3.chatapp.utils.IpAddressUtils.getClientIpAddress;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccessRestrictionFilter extends OncePerRequestFilter {

    private static final String ANONYMOUS_USER = "anonymousUser";
    private static final Map<String, Set<String>> ENDPOINT_ALLOWED_METHODS = Map.ofEntries(
            Map.entry("/api/v1/auth/**", Set.of("GET", "POST", "PUT", "DELETE", "PATCH")),
            Map.entry("/health", Set.of("GET")),
            Map.entry("/ws/**", Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")),
            Map.entry("/api/v1/users/me", Set.of("GET")),
            Map.entry("/api/v1/users/send-email-verification", Set.of("POST")),
            Map.entry("/api/v1/users/verify", Set.of("PATCH")),
            Map.entry("/api/v1/users/send-phone-verification", Set.of("POST")),
            Map.entry("/api/v1/users/verify-phone", Set.of("PATCH")),
            Map.entry("/api/v1/chat-rooms/**", Set.of("GET")),
            Map.entry("/api/v1/chatting/messaging-availability", Set.of("GET")),
            Map.entry("/api/v1/ban-appeals/**", Set.of("GET", "POST")),
            Map.entry("/api/v1/id-verification/**", Set.of("GET", "POST"))
    );
    // The only API surface reachable with an active ban: the appeal flow, identity
    // lookup, the ping bootstrap call, logout and login. Deliberately excludes
    // /ws/** so banned users cannot hold a live socket, and everything else keeps
    // returning the ban-shaped 403. /auth/ping must stay reachable: the frontend
    // blocks all session hydration on it, so banning it deadlocks the /banned page
    // itself. /auth/login must stay reachable because banned users may log in (see
    // AuthenticationServiceImpl.login) and, more importantly, a browser holding a
    // stale banned session token would otherwise 403 a *different* user's login.
    // Register endpoints stay excluded on purpose.
    private static final Map<String, Set<String>> BANNED_USER_ALLOWED_ENDPOINTS = Map.of(
            "/api/v1/ban-appeals/**", Set.of("GET", "POST"),
            "/api/v1/users/me", Set.of("GET"),
            "/api/v1/auth/ping", Set.of("GET"),
            "/api/v1/auth/logout", Set.of("POST"),
            "/api/v1/auth/login", Set.of("POST")
    );
    private final BanCacheService banCacheService;
    private final ObjectMapper objectMapper;
    private final IpService ipService;
    private final UserRepository userRepository;
    private final BanMapper banMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ipAddress = getClientIpAddress(request);
        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = getAuthenticatedUserId(auth);

        if (userId != null) {
            Ban userBan = banCacheService.getBanByUserId(userId);
            if (isBanActive(userBan) && !isBannedUserAllowedRequest(requestUri, method)) {
                respondWithBan(response, userBan);
                return;
            }
        }

        if (userId != null && isIdVerificationRequiredForRequest(requestUri, method, userId)) {
            respondWithIdVerificationRequired(response);
            return;
        }

        RequiredVerificationEnum verificationRequired = ipService.getRequiredVerification(ipAddress);
        if (isVerificationRequiredForRequest(verificationRequired, requestUri, method, userId)) {
            respondWithVerificationRequired(response, verificationRequired);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isIdVerificationRequiredForRequest(String requestUri, String method, Long userId) {
        boolean idVerificationRequired = userRepository.findById(userId)
                .map(user -> user.getIdVerificationStatus() == IdVerificationStatus.REQUIRED
                        || user.getIdVerificationStatus() == IdVerificationStatus.PENDING
                        || user.getIdVerificationStatus() == IdVerificationStatus.REJECTED)
                .orElse(false);

        return idVerificationRequired && !isRequestOnAllowedEndpoint(requestUri, method);
    }

    private void respondWithIdVerificationRequired(HttpServletResponse response) throws IOException {
        ErrorDTO verificationDTO = new ErrorDTO(
                HttpServletResponse.SC_FORBIDDEN,
                "Verification Required",
                "Please complete the identity verification to access this endpoint",
                LocalDateTime.now()
        );

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(verificationDTO));
    }

    private void respondWithVerificationRequired(HttpServletResponse response,
                                                 RequiredVerificationEnum verificationType) throws IOException {
        ErrorDTO verificationDTO = new ErrorDTO(
                HttpServletResponse.SC_FORBIDDEN,
                "Verification Required",
                "Please complete the " + verificationType.name().toLowerCase(Locale.ROOT) + " verification to access this endpoint",
                LocalDateTime.now()
        );

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(verificationDTO));
    }

    private boolean isVerificationRequiredForRequest(RequiredVerificationEnum verificationRequired,
                                                     String requestUri,
                                                     String method,
                                                     Long userId) {
        if (verificationRequired == RequiredVerificationEnum.NONE) {
            return false;
        }

        if (isVerificationSatisfied(verificationRequired, userId)) {
            return false;
        }

        return !isRequestOnAllowedEndpoint(requestUri, method);
    }

    private boolean isRequestOnAllowedEndpoint(String requestUri, String method) {
        for (Map.Entry<String, Set<String>> entry : ENDPOINT_ALLOWED_METHODS.entrySet()) {
            String pattern = entry.getKey();
            Set<String> allowedMethods = entry.getValue();

            if (pathMatcher.match(pattern, requestUri)) {
                return allowedMethods.contains(method.toUpperCase(Locale.ROOT));
            }
        }

        return false;
    }

    private boolean isVerificationSatisfied(RequiredVerificationEnum verificationRequired, Long userId) {
        if (userId == null) {
            return false;
        }

        return userRepository.findById(userId)
                .map(user -> switch (verificationRequired) {
                    case EMAIL -> user.isVerified();
                    case PHONE -> user.getPhoneNumberVerificationDate() != null;
                    case NONE -> true;
                })
                .orElse(false);
    }

    private boolean isBanActive(Ban ban) {
        return ban != null && ban.isActive();
    }

    private boolean isBannedUserAllowedRequest(String requestUri, String method) {
        for (Map.Entry<String, Set<String>> entry : BANNED_USER_ALLOWED_ENDPOINTS.entrySet()) {
            if (pathMatcher.match(entry.getKey(), requestUri)) {
                return entry.getValue().contains(method.toUpperCase(Locale.ROOT));
            }
        }
        return false;
    }

    private void respondWithBan(HttpServletResponse response, Ban ban) throws IOException {
        BanResponseDTO banDTO = createBanResponseDTO(ban);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(banDTO));
    }

    private BanResponseDTO createBanResponseDTO(Ban ban) {
        return banMapper.toUserFacingDto(ban);
    }

    private Long getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String name = authentication.getName();
        if (name == null || name.isBlank() || ANONYMOUS_USER.equals(name)) {
            return null;
        }

        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            log.debug("Unable to parse authenticated principal name '{}' as user id", name);
            return null;
        }
    }
}
