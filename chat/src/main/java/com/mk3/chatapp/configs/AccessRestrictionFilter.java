package com.mk3.chatapp.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.dtos.ErrorDTO;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.enums.RequiredVerificationEnum;
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
    private static final Map<String, Set<String>> ENDPOINT_ALLOWED_METHODS = Map.of(
            "/api/v1/auth/**", Set.of("GET", "POST", "PUT", "DELETE", "PATCH"),
            "/health", Set.of("GET"),
            "/ws/**", Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"),
            "/api/v1/users/me", Set.of("GET"),
            "/api/v1/users/send-email-verification", Set.of("POST"),
            "/api/v1/users/verify", Set.of("PATCH"),
            "/api/v1/users/send-phone-verification", Set.of("POST"),
            "/api/v1/users/verify-phone", Set.of("PATCH"),
            "/api/v1/chat-rooms/**", Set.of("GET"),
            "/api/v1/chatting/messaging-availability", Set.of("GET")
    );
    private final BanCacheService banCacheService;
    private final ObjectMapper objectMapper;
    private final IpService ipService;
    private final UserRepository userRepository;
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
            if (isBanActive(userBan)) {
                respondWithBan(response, userBan);
                return;
            }
        }

        RequiredVerificationEnum verificationRequired = ipService.getRequiredVerification(ipAddress);
        if (isVerificationRequiredForRequest(verificationRequired, requestUri, method, userId)) {
            respondWithVerificationRequired(response, verificationRequired);
            return;
        }

        filterChain.doFilter(request, response);
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

        for (Map.Entry<String, Set<String>> entry : ENDPOINT_ALLOWED_METHODS.entrySet()) {
            String pattern = entry.getKey();
            Set<String> allowedMethods = entry.getValue();

            if (pathMatcher.match(pattern, requestUri)) {
                return !allowedMethods.contains(method.toUpperCase(Locale.ROOT));
            }
        }

        return true;
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

    private void respondWithBan(HttpServletResponse response, Ban ban) throws IOException {
        BanResponseDTO banDTO = createBanResponseDTO(ban);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(banDTO));
    }

    private BanResponseDTO createBanResponseDTO(Ban ban) {
        ReportType userFacingReportType = ban.getReportType().toUserFacingReportType();
        String userFacingDescription = ban.getReportType().toUserFacingDescription(ban.getDescription());

        return new BanResponseDTO(
                ban.getId(),
                null,
                null,
                ban.getIpAddress(),
                ban.getUserAgent(),
                userFacingDescription,
                ban.getExpiresAt() == null ? null : ban.getExpiresAt().toString(),
                true,
                ban.getType(),
                userFacingReportType
        );
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
