package com.mk3.chatapp.configs;

import com.mk3.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

/**
 * Chat-module security wiring. The domain-specific {@link UserDetailsService} (loads the unified
 * {@code User} by id) and the {@link AuthenticationProvider} live here because they reference chat's
 * {@link UserService}. The generic {@code PasswordEncoder} and {@code AuthenticationManager} now come
 * from {@code com.allchat.identity.security.IdentitySecurityConfig} in the shared-identity module.
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    @Bean
    public UserDetailsService userDetailsService(UserService userService) {
        return userService::loadUserByUsername;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserService userService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService(userService));
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
