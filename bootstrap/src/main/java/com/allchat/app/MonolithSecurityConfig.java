package com.allchat.app;

import com.mk3.chatapp.configs.AccessRestrictionFilter;
import com.mk3.chatapp.configs.RateLimitFilter;
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

import java.util.List;

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
                        // --- ads-portal public endpoints (namespaced under /api/v1/ads-portal) ---
                        // Ads auth is now unified onto chat's /api/v1/auth/** (above); the ads
                        // module no longer exposes its own /ads-portal/auth/* endpoints.
                        .requestMatchers("/api/v1/ads-portal/ads/serve").permitAll()
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfig = new CorsConfiguration();
                    // Permissive in dev; both the chat and ads frontends are allowed. Tighten per-origin in prod.
                    corsConfig.setAllowedOriginPatterns(List.of("*"));
                    corsConfig.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS", "PUT"));
                    corsConfig.setAllowedHeaders(List.of("*"));
                    corsConfig.setAllowCredentials(true);
                    return corsConfig;
                }))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(accessRestrictionFilter, UsernamePasswordAuthenticationFilter.class)
                .authenticationProvider(authenticationProvider);

        return http.build();
    }
}
