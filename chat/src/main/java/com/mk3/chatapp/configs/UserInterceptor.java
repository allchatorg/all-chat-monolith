package com.mk3.chatapp.configs;

import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.enums.RequiredVerificationEnum;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.BanCacheService;
import com.mk3.chatapp.services.IpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserInterceptor implements ChannelInterceptor {
    private static final String ANONYMOUS_USER = "anonymousUser";

    private final IpService ipService;
    private final BanCacheService banCacheService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Get username from session attributes (set by handshake interceptor)
            String username = accessor.getSessionAttributes() == null
                    ? null
                    : (String) accessor.getSessionAttributes().get("username");

            if (username != null) {
                accessor.setUser(new UsernamePasswordAuthenticationToken(username, null));
            }

            Long userId = resolveUserId(accessor);
            if (isActiveUserBan(userId)) {
                log.debug("Blocked STOMP CONNECT for banned user {}", userId);
                throw new MessagingException("Connection refused: user is banned");
            }
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            Long userId = resolveUserId(accessor);

            if (isActiveUserBan(userId)) {
                log.debug("Blocked STOMP SEND for banned user {}", userId);
                return null;
            }

            // Flagged users may still connect and subscribe (so they receive the
            // ID_VERIFICATION_RESULT notification live) but cannot send messages.
            if (requiresIdVerification(userId)) {
                log.debug("Blocked STOMP SEND for user {} pending identity verification", userId);
                return null;
            }

            String ipAddress = accessor.getSessionAttributes() == null
                    ? null
                    : (String) accessor.getSessionAttributes().get("ipAddress");

            RequiredVerificationEnum requiredVerification = ipAddress == null
                    ? RequiredVerificationEnum.NONE
                    : ipService.getRequiredVerification(ipAddress);

            if (requiredVerification != RequiredVerificationEnum.NONE
                    && !isVerificationSatisfied(userId, requiredVerification)) {
                log.debug("Blocked STOMP SEND for user {} due to required {} verification", userId, requiredVerification);
                return null;
            }
        }

        return message;
    }

    private Long resolveUserId(StompHeaderAccessor accessor) {
        String username = accessor.getUser() != null
                ? accessor.getUser().getName()
                : accessor.getSessionAttributes() == null
                ? null
                : (String) accessor.getSessionAttributes().get("username");

        if (username == null || username.isBlank() || ANONYMOUS_USER.equals(username)) {
            return null;
        }

        try {
            return Long.parseLong(username);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isActiveUserBan(Long userId) {
        if (userId == null) {
            return false;
        }
        var ban = banCacheService.getBanByUserId(userId);
        return ban != null && ban.isActive();
    }

    private boolean requiresIdVerification(Long userId) {
        if (userId == null) {
            return false;
        }

        return userRepository.findById(userId)
                .map(user -> user.getIdVerificationStatus() == IdVerificationStatus.REQUIRED
                        || user.getIdVerificationStatus() == IdVerificationStatus.PENDING
                        || user.getIdVerificationStatus() == IdVerificationStatus.REJECTED)
                .orElse(false);
    }

    private boolean isVerificationSatisfied(Long userId, RequiredVerificationEnum requiredVerification) {
        if (userId == null) {
            return false;
        }

        return userRepository.findById(userId)
                .map(user -> switch (requiredVerification) {
                    case EMAIL -> user.isVerified();
                    case PHONE -> user.getPhoneNumberVerificationDate() != null;
                    case NONE -> true;
                })
                .orElse(false);
    }
}
