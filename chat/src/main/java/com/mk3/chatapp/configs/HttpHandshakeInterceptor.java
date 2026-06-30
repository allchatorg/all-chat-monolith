package com.mk3.chatapp.configs;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

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
                            attributes.put("username", username);
                            attributes.put("principal", authentication.getPrincipal());
                        }
                    }
                }
            }
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
