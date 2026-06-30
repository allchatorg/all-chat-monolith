package com.allchat.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Domain-agnostic security primitives shared by every module in the monolith.
 *
 * <p>The {@link PasswordEncoder} and {@link AuthenticationManager} live here so that no single
 * domain module "owns" them and both the chat and ads modules authenticate against identical
 * primitives. The domain-specific {@code UserDetailsService} and {@code AuthenticationProvider}
 * (which know how to load the unified {@code User}) stay in the chat module, since they reference
 * chat's {@code UserService}.
 */
@Configuration
public class IdentitySecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
