package com.allchat.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.ConfigureRedisAction;

/**
 * App-level Redis HTTP-session infrastructure.
 *
 * <p>Lives in {@code bootstrap} alongside {@code @EnableRedisIndexedHttpSession}
 * (on {@link AllChatApplication}) because session storage is an application-wide
 * concern, not a chat- or ads-module one. The connection factory itself is
 * auto-configured by Spring Boot from {@code spring.data.redis.*}; each module
 * owns its own {@code RedisTemplate} (chat's {@code redisTemplate},
 * ads' {@code adsRedisTemplate}).
 */
@Configuration
public class RedisSessionConfig {

    /**
     * Disables Spring Session's {@code CONFIG SET notify-keyspace-events} call on
     * startup. Managed/cloud Redis blocks the {@code CONFIG} command, and the
     * indexed session repository would otherwise fail to initialize.
     *
     * <p>Must be {@code static}: it is consumed during the
     * {@code BeanFactoryPostProcessor} phase, before regular bean instantiation.
     */
    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }
}
