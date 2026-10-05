package com.mk3.chatapp.configs;

import com.mk3.chatapp.services.RateLimiterService;
import com.mk3.chatapp.services.TypingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TypingChannelInterceptor implements ChannelInterceptor {
    // Lazy to avoid a configuration -> broker template -> configuration dependency cycle.
    private final ObjectProvider<TypingService> typingService;
    private final RateLimiterService rateLimiter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        var command = headers.getCommand();
        String destination = headers.getDestination();
        if (command == StompCommand.SEND && destination != null
                && destination.startsWith("/topic/chat-typing")) return null;

        if (command == StompCommand.SUBSCRIBE && destination != null) {
            // Pattern subscriptions could otherwise bypass the exact room permission check.
            if (destination.matches(".*[?*{}].*")) return null;
            if (destination.startsWith("/topic/chat-typing")) {
                if (!destination.matches("/topic/chat-typing\\.[1-9][0-9]*")) return null;
                try {
                    Long userId = userId(headers);
                    if (!allowed(userId)) return null;
                    Long roomId = Long.valueOf(destination.substring(TypingService.TOPIC_PREFIX.length()));
                    String ip = headers.getSessionAttributes() == null ? null
                            : (String) headers.getSessionAttributes().get("ipAddress");
                    return typingService.getObject().subscribe(headers.getSessionId(), headers.getSubscriptionId(),
                            userId, roomId, ip) ? message : null;
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        if (command == StompCommand.SEND && TypingService.DESTINATION.equals(destination)) {
            try {
                return allowed(userId(headers)) ? message : null;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (command == StompCommand.UNSUBSCRIBE) {
            typingService.getObject().unsubscribe(headers.getSessionId(), headers.getSubscriptionId());
        }
        return message;
    }

    private boolean allowed(Long userId) {
        return userId != null && rateLimiter.allow("typing:user:" + userId, 120, Duration.ofMinutes(1));
    }

    private Long userId(StompHeaderAccessor headers) {
        return headers.getUser() == null ? null : Long.valueOf(headers.getUser().getName());
    }
}
