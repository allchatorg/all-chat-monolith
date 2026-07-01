package com.example.adsportalbe.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Dedicated Redis configuration for the ads module.
 *
 * <p>The ads impression pipeline (cache services + the scheduled
 * {@code AdImpressionProcessingJob}) was written and tested against the
 * standalone ads app's serializer: {@link LaissezFaireSubTypeValidator} with
 * {@code NON_FINAL} default typing (WRAPPER_ARRAY inclusion), {@code JavaTimeModule},
 * and {@code WRITE_DATES_AS_TIMESTAMPS} disabled. When the apps were merged the
 * ads {@code RedisConfig} was dropped, so the ads services silently picked up
 * chat's {@code RedisTemplate} (which uses {@code @class}-property typing and
 * timestamp dates). That divergence made cached {@code AdImpressionDto}s
 * deserialize to the wrong type, so the processing job silently dropped them and
 * impressions never persisted.
 *
 * <p>This restores the original ads serializer as a distinctly named bean
 * ({@code adsRedisTemplate}) that the ads cache services bind to via
 * {@code @Qualifier}. It reuses the single existing {@link RedisConnectionFactory}
 * rather than defining a second one. Chat's {@code redisTemplate} is marked
 * {@code @Primary}, so unqualified by-type injections keep resolving to it.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> adsRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
