package com.allchat.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.configs.AccessRestrictionFilter;
import com.mk3.chatapp.configs.RateLimitFilter;
import com.mk3.chatapp.dtos.ErrorDTO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * The single, consolidated security configuration for the whole monolith.
 *
 * <p>It replaces the two former per-app chains. Authentication is session-based (chat's model):
 * the ads module's former stateless-JWT chain is gone, and ads endpoints now ride the same
 * {@code X-Auth-Token} session. Chat's custom filters (rate limiting, access restriction) are
 * preserved. Lives in the bootstrap module because it must see chat's filters and both modules'
 * URL namespaces.
 */
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class MonolithSecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final AccessRestrictionFilter accessRestrictionFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // --- chat public endpoints ---
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Stripe Identity webhook: authenticated by signature verification, not session.
                        .requestMatchers(HttpMethod.POST, "/api/v1/id-verification/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/pro/webhook").permitAll()
                        // --- ads-portal public endpoints (namespaced under /api/v1/ads-portal) ---
                        // Ads auth is now unified onto chat's /api/v1/auth/** (above); the ads
                        // module no longer exposes its own /ads-portal/auth/* endpoints.
                        .requestMatchers("/api/v1/ads-portal/ads/serve").permitAll()
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(apiCorsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, 401, "Unauthorized", "Please sign in to continue"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, 403, "Forbidden", "You do not have access to this resource")))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(accessRestrictionFilter, UsernamePasswordAuthenticationFilter.class)
                .authenticationProvider(authenticationProvider);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource apiCorsConfigurationSource() {
        var corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(List.of("*"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS", "PUT"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setExposedHeaders(List.of("X-Request-Id"));
        corsConfig.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }

    private void writeSecurityError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorDTO(
                status, error, message, LocalDateTime.now())));
    }
}
