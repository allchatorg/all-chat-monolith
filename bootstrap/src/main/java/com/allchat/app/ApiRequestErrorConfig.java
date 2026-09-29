package com.allchat.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.dtos.ErrorDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/** Encloses session restoration AND response-time session persistence. */
@Configuration
@RequiredArgsConstructor
public class ApiRequestErrorConfig {
    private final ObjectMapper objectMapper;

    @Bean
    public FilterRegistrationBean<ApiRequestErrorFilter> apiRequestErrorFilter(
            @Qualifier("apiCorsConfigurationSource") CorsConfigurationSource corsConfigurationSource) {
        var registration = new FilterRegistrationBean<>(new ApiRequestErrorFilter(objectMapper, corsConfigurationSource));
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 1);
        registration.addUrlPatterns("/api/v1/*");
        return registration;
    }

    @Slf4j
    @RequiredArgsConstructor
    static class ApiRequestErrorFilter extends OncePerRequestFilter {
        private final ObjectMapper objectMapper;
        private final CorsConfigurationSource corsConfigurationSource;

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String requestId = UUID.randomUUID().toString();
            String previousRequestId = MDC.get("requestId");
            MDC.put("requestId", requestId);
            response.setHeader("X-Request-Id", requestId);
            long started = System.nanoTime();
            boolean unhandledFailure = false;
            try {
                filterChain.doFilter(request, response);
            } catch (Exception exception) {
                unhandledFailure = true;
                log.error("API request failed requestId={} method={} path={} committed={}",
                        requestId, request.getMethod(), request.getRequestURI(), response.isCommitted(), exception);
                if (!response.isCommitted()) {
                    // Keep CORS and correlation headers; discard a partial response body.
                    response.resetBuffer();
                    // Session restoration can fail before Security's CorsFilter.
                    // Apply the same origin policy so browsers can read this error.
                    if (!new DefaultCorsProcessor().processRequest(
                            corsConfigurationSource.getCorsConfiguration(request), request, response)) return;
                    response.setHeader("Content-Length", null);
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType("application/json");
                    byte[] body = objectMapper.writeValueAsBytes(new ErrorDTO(
                            500, "Internal Server Error", "An unexpected error occurred", LocalDateTime.now()));
                    try {
                        response.getOutputStream().write(body);
                    } catch (IllegalStateException writerAlreadySelected) {
                        response.getWriter().write(new String(body, StandardCharsets.UTF_8));
                    }
                }
            } finally {
                if (response.getStatus() >= 400 || unhandledFailure) {
                    log.warn("API response requestId={} method={} path={} status={} durationMs={}",
                            requestId, request.getMethod(), request.getRequestURI(), response.getStatus(),
                            (System.nanoTime() - started) / 1_000_000);
                }
                if (previousRequestId == null) MDC.remove("requestId");
                else MDC.put("requestId", previousRequestId);
            }
        }
    }
}
