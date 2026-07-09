package com.mk3.chatapp.configs;

import com.mk3.chatapp.services.BanCacheService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

import static com.mk3.chatapp.utils.IpAddressUtils.getClientIpAddress;

@Component
@RequiredArgsConstructor
public class HttpHandshakeInterceptor implements HandshakeInterceptor {

    private static final String ANONYMOUS_USER = "anonymousUser";

    @Autowired
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final BanCacheService banCacheService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            attributes.put("ipAddress", getClientIpAddress(httpServletRequest));

            String sessionId = servletRequest.getServletRequest().getParameter("token");

            if (sessionId != null) {
                Session session = sessionRepository.findById(sessionId);
                if (session != null) {
                    Object context = session.getAttribute("SPRING_SECURITY_CONTEXT");
                    if (context instanceof SecurityContext securityContext) {
                        Authentication authentication = securityContext.getAuthentication();
                        if (authentication != null && authentication.isAuthenticated()) {
                            String username = authentication.getName();

                            if (isActiveUserBan(parseUserId(username))) {
                                response.setStatusCode(HttpStatus.FORBIDDEN);
                                return false;
                            }

                            attributes.put("username", username);
                            attributes.put("principal", authentication.getPrincipal());
                        }
                    }
                }
            }
        }

        return true;
    }

    private Long parseUserId(String username) {
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

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
