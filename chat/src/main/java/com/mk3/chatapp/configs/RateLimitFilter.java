package com.mk3.chatapp.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.dtos.ErrorDTO;
import com.mk3.chatapp.enums.RequiredVerificationEnum;
import com.mk3.chatapp.services.IpService;
import com.mk3.chatapp.services.RateLimiterService;
import com.mk3.chatapp.utils.IpAddressUtils;
import jakarta.annotation.PostConstruct;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {
    private static final String ANONYMOUS_USER = "anonymousUser";
    private static final String API_PREFIX = "/api/v1/";
    private static final Set<String> ALL_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration ONE_DAY = Duration.ofDays(1);

    private final RateLimiterService rateLimiterService;
    private final IpService ipService;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private List<RateLimitRule> rules = List.of();

    private static RateLimitRule rule(String action,
                                      Set<String> methods,
                                      String pattern,
                                      RateLimitScope scope,
                                      Duration window,
                                      long limit,
                                      Long flaggedLimit,
                                      String message) {
        return new RateLimitRule(action, methods, pattern, scope, window, limit, flaggedLimit, message);
    }

    @PostConstruct
    void initializeRules() {
        RateLimitProperties.Sensitive sensitive = rateLimitProperties.getSensitive();
        RateLimitProperties.Controllers controllers = rateLimitProperties.getControllers();

        this.rules = List.of(
                // Auth
                rule("register", Set.of("POST"), "/api/v1/auth/register*", RateLimitScope.IP, ONE_HOUR,
                        sensitive.getRegisterPerIpNormalPerHour(),
                        sensitive.getRegisterPerIpFlaggedPerHour(),
                        "Registration rate limit exceeded for your IP."),
                rule("login", Set.of("POST"), "/api/v1/auth/login", RateLimitScope.IP, TEN_MINUTES,
                        sensitive.getLoginPerIpTenMinutes(), null,
                        "Too many login attempts. Please try again soon."),
                rule("forgot_password", Set.of("POST"), "/api/v1/auth/forgot-password", RateLimitScope.IP, ONE_HOUR,
                        sensitive.getForgotPasswordPerIpPerHour(), null,
                        "Forgot-password rate limit exceeded for your IP."),
                rule("phone_password_reset_verify", Set.of("POST"), "/api/v1/auth/forgot-password/verify-phone-code",
                        RateLimitScope.IP, ONE_HOUR,
                        sensitive.getPhonePasswordResetVerifyPerIpPerHour(), null,
                        "Phone password reset verification attempts exceeded for your IP."),
                rule("reset_password", Set.of("POST"), "/api/v1/auth/reset-password", RateLimitScope.IP, ONE_HOUR,
                        sensitive.getResetPasswordPerIpPerHour(), null,
                        "Reset-password rate limit exceeded for your IP."),
                rule("claim_account", Set.of("PATCH"), "/api/v1/auth/claim-account", RateLimitScope.IP, ONE_HOUR,
                        sensitive.getClaimAccountPerIpPerHour(), null,
                        "Claim-account rate limit exceeded for your IP."),
                rule("auth_controller", ALL_METHODS, "/api/v1/auth/**", RateLimitScope.IP, ONE_HOUR,
                        controllers.getAuthPerHour(), null,
                        "Too many authentication requests. Please try again later."),

                // Users
                rule("send_phone_verification", Set.of("POST"), "/api/v1/users/send-phone-verification",
                        RateLimitScope.IP, ONE_HOUR,
                        sensitive.getPhoneVerificationPerIpNormalPerHour(),
                        sensitive.getPhoneVerificationPerIpFlaggedPerHour(),
                        "Phone verification requests exceeded for your IP."),
                rule("change_username", Set.of("PATCH"), "/api/v1/users/change-username",
                        RateLimitScope.USER_OR_IP, ONE_DAY,
                        sensitive.getChangeUsernamePerUserPerDay(),
                        null,
                        "You can only change username up to 5 times per day."),
                rule("users_controller", ALL_METHODS, "/api/v1/users/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getUsersPerHour(), null,
                        "Too many user requests. Please try again later."),

                // Chatting
                rule("send_message", Set.of("POST"), "/api/v1/chatting/messages", RateLimitScope.USER_OR_IP, ONE_MINUTE,
                        sensitive.getMessagesPerMinute(), null,
                        "You are sending too many messages. Please wait a bit."),
                rule("chatting_controller", ALL_METHODS, "/api/v1/chatting/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getChattingPerHour(), null,
                        "Too many chatting requests. Please try again later."),

                // Chat rooms
                rule("top_reacted_messages_poll", Set.of("GET"), "/api/v1/chat-rooms/*/messages/top-reacted",
                        RateLimitScope.USER_OR_IP, ONE_MINUTE,
                        sensitive.getTopReactedMessagesPerMinute(), null,
                        "Too many top-reacted polling requests. Please slow down."),
                rule("room_messages_poll", Set.of("GET"), "/api/v1/chat-rooms/*/messages",
                        RateLimitScope.USER_OR_IP, ONE_MINUTE,
                        sensitive.getRoomMessagesPerMinute(), null,
                        "Too many message polling requests. Please slow down."),
                rule("room_heartbeat", Set.of("POST"), "/api/v1/chat-rooms/heartbeat",
                        RateLimitScope.USER_OR_IP, ONE_MINUTE,
                        sensitive.getRoomHeartbeatPerMinute(), null,
                        "Too many heartbeat requests. Please slow down."),
                rule("room_active_switch", Set.of("PATCH"), "/api/v1/chat-rooms/*/active",
                        RateLimitScope.USER_OR_IP, ONE_MINUTE,
                        sensitive.getRoomActiveSwitchPerMinute(), null,
                        "Too many active-room updates. Please slow down."),
                rule("chat_rooms_controller", ALL_METHODS, "/api/v1/chat-rooms/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getChatRoomsPerHour(), null,
                        "Too many chat-room requests. Please try again later."),

                // Private chats (must precede the controller fallback so the exact-path create rule wins)
                rule("private_chat_create", Set.of("POST"), "/api/v1/private-chats", RateLimitScope.USER_OR_IP, ONE_MINUTE,
                        sensitive.getPrivateChatCreatePerMinute(), null,
                        "You are starting conversations too quickly. Please slow down."),
                rule("private_chats_controller", ALL_METHODS, "/api/v1/private-chats/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getPrivateChatsPerHour(), null,
                        "Too many private-chat requests. Please try again later."),

                // Ads
                rule("serve_ad", Set.of("POST"), "/api/v1/ads/serve", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        sensitive.getAdsServePerHour(), null,
                        "Ad serving limit exceeded. Please try again later."),
                rule("ad_click", Set.of("POST"), "/api/v1/ads/*/click", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        sensitive.getAdsClickPerHour(), null,
                        "Too many ad interactions. Please try again later."),
                rule("ads_controller", ALL_METHODS, "/api/v1/ads/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getAdsPerHour(), null,
                        "Too many ads requests. Please try again later."),

                // Ban appeals (submission attempts are capped daily; successes are capped at
                // one per ban by the unique constraint on ban_appeal.ban_id)
                rule("ban_appeal_submit", Set.of("POST"), "/api/v1/ban-appeals", RateLimitScope.USER_OR_IP, ONE_DAY,
                        sensitive.getBanAppealSubmitPerUserPerDay(), null,
                        "Too many appeal submissions. Please try again later."),
                rule("ban_appeals_controller", ALL_METHODS, "/api/v1/ban-appeals/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getBanAppealsPerHour(), null,
                        "Too many ban-appeal requests. Please try again later."),

                // Other controllers
                rule("settings_controller", ALL_METHODS, "/api/v1/settings/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getSettingsPerHour(), null,
                        "Too many settings requests. Please try again later."),
                rule("report_cases_controller", ALL_METHODS, "/api/v1/report-cases/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getReportCasesPerHour(), null,
                        "Too many report-case requests. Please try again later."),
                rule("admin_controller", ALL_METHODS, "/api/v1/admin/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        controllers.getAdminPerHour(), null,
                        "Too many admin requests. Please try again later."),

                // Safety fallback for future /api/v1 controllers
                rule("api_fallback", ALL_METHODS, "/api/v1/**", RateLimitScope.USER_OR_IP, ONE_HOUR,
                        rateLimitProperties.getApiFallbackPerHour(), null,
                        "Too many API requests. Please try again later.")
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = IpAddressUtils.getClientIpAddress(request);
        String uri = request.getRequestURI();
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        Long userId = getAuthenticatedUserId();

        if (!allowIp("global_api", ip, rateLimitProperties.getGlobalPerIpPerHour(), ONE_HOUR)) {
            write429(response, "Too many requests from your IP. Please try again later.");
            return;
        }

        try {
            // Email verification uses both IP and user buckets.
            if ("POST".equals(method) && matches(uri, "/api/v1/users/send-email-verification")) {
                RateLimitProperties.Sensitive sensitive = rateLimitProperties.getSensitive();
                if (!allowIp("send_email_verification", ip, sensitive.getEmailVerificationPerIpPerHour())) {
                    write429(response, "Email verification requests exceeded for your IP.");
                    return;
                }
                if (userId != null && !allowUser("send_email_verification", userId, sensitive.getEmailVerificationPerUserPerHour())) {
                    write429(response, "Email verification requests exceeded for your account.");
                    return;
                }
            }

            if ("POST".equals(method) && matches(uri, "/api/v1/users/request-email-update")) {
                RateLimitProperties.Sensitive sensitive = rateLimitProperties.getSensitive();
                if (!allowIp("request_email_update", ip, sensitive.getRequestEmailUpdatePerIpPerHour())) {
                    write429(response, "Email update requests exceeded for your IP.");
                    return;
                }
                if (userId != null && !allowUser("request_email_update", userId, sensitive.getRequestEmailUpdatePerUserPerHour())) {
                    write429(response, "Email update requests exceeded for your account.");
                    return;
                }
            }

            RateLimitRule rule = findRule(method, uri);
            if (rule != null && !allowByRule(rule, ip, userId)) {
                write429(response, rule.message());
                return;
            }
        } catch (Exception e) {
            log.error("Rate limiting check failed", e);
            // Fail-open to avoid blocking due to internal errors.
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        return !uri.startsWith(API_PREFIX);
    }

    private RateLimitRule findRule(String method, String uri) {
        for (RateLimitRule rule : rules) {
            if (rule.matches(method, uri, pathMatcher)) {
                return rule;
            }
        }
        return null;
    }

    private boolean allowByRule(RateLimitRule rule, String ip, Long userId) {
        long limit = rule.limit();
        if (rule.flaggedLimit() != null && isIpFlagged(ip)) {
            limit = rule.flaggedLimit();
        }

        return switch (rule.scope()) {
            case IP -> allowIp(rule.action(), ip, limit, rule.window());
            case USER_OR_IP -> userId != null
                    ? allowUser(rule.action(), userId, limit, rule.window())
                    : allowIp(rule.action(), ip, limit, rule.window());
        };
    }

    private boolean matches(String uri, String pattern) {
        return pathMatcher.match(pattern, uri);
    }

    private boolean isIpFlagged(String ip) {
        return ipService.getRequiredVerification(ip) != RequiredVerificationEnum.NONE;
    }

    private boolean allowIp(String action, String ip, long limit) {
        return allowIp(action, ip, limit, ONE_HOUR);
    }

    private boolean allowIp(String action, String ip, long limit, Duration window) {
        String key = rateLimiterService.ipKey(action, ip);
        return rateLimiterService.allow(key, limit, window);
    }

    private boolean allowUser(String action, Long userId, long limit) {
        return allowUser(action, userId, limit, ONE_HOUR);
    }

    private boolean allowUser(String action, Long userId, long limit, Duration window) {
        String key = rateLimiterService.userKey(action, userId);
        return rateLimiterService.allow(key, limit, window);
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            if (auth.getName() == null || auth.getName().isBlank() || ANONYMOUS_USER.equals(auth.getName())) {
                return null;
            }
            try {
                return Long.parseLong(auth.getName());
            } catch (NumberFormatException ignored) {
                log.debug("Unable to parse authenticated principal '{}' as user id", auth.getName());
            }
        }
        return null;
    }

    private void write429(HttpServletResponse response, String message) throws IOException {
        ErrorDTO error = new ErrorDTO(429, "Too Many Requests", message, LocalDateTime.now());
        response.setContentType("application/json");
        response.setStatus(429);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    private enum RateLimitScope {
        IP,
        USER_OR_IP
    }

    private record RateLimitRule(
            String action,
            Set<String> methods,
            String pattern,
            RateLimitScope scope,
            Duration window,
            long limit,
            Long flaggedLimit,
            String message
    ) {
        boolean matches(String method, String uri, AntPathMatcher pathMatcher) {
            return methods.contains(method) && pathMatcher.match(pattern, uri);
        }
    }
}
