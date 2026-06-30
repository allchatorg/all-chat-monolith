package com.mk3.chatapp.configs;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.Collections;
import java.util.List;

public class HeaderHttpSessionIdResolver implements HttpSessionIdResolver {
    private final String headerName;

    public HeaderHttpSessionIdResolver(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        String sessionId = request.getHeader(headerName);
        return sessionId == null ? Collections.emptyList() : Collections.singletonList(sessionId);
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {

    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {

    }
}
