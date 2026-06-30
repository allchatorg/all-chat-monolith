package com.mk3.chatapp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {
    private static final String FIXED_WINDOW_PREFIX = "rlfw";
    private static final Duration EXPIRY_GRACE = Duration.ofSeconds(5);
    private static final long REDIS_ERROR_LOG_INTERVAL_MS = 60_000;
    private static final int LOCAL_CLEANUP_THRESHOLD = 50_000;
    private static final int LOCAL_CLEANUP_BATCH = 1_000;

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, LocalCounter> localCounters = new ConcurrentHashMap<>();
    private final AtomicLong lastRedisErrorLogAt = new AtomicLong(0L);

    public boolean allow(String key, long limit, Duration window) {
        if (limit <= 0 || window == null || window.isNegative() || window.isZero()) {
            return false;
        }

        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                return allowWithRedis(redisTemplate, key, limit, window);
            }
        } catch (Exception e) {
            logRedisFailure(e);
        }

        return allowWithLocalCounter(key, limit, window);
    }

    private boolean allowWithRedis(StringRedisTemplate redisTemplate, String key, long limit, Duration window) {
        WindowKey windowKey = buildWindowKey(key, window);
        Long count = redisTemplate.opsForValue().increment(windowKey.value());

        if (count == null) {
            return allowWithLocalCounter(key, limit, window);
        }

        if (count == 1L) {
            redisTemplate.expire(windowKey.value(), window.plus(EXPIRY_GRACE));
        }

        return count <= limit;
    }

    private boolean allowWithLocalCounter(String key, long limit, Duration window) {
        long nowMillis = System.currentTimeMillis();
        long ttlMillis = window.toMillis() + EXPIRY_GRACE.toMillis();
        WindowKey windowKey = buildWindowKey(key, window);

        LocalCounter counter = localCounters.compute(windowKey.value(), (k, current) -> {
            if (current == null || current.expiresAtEpochMillis() <= nowMillis) {
                return new LocalCounter(new AtomicLong(0L), nowMillis + ttlMillis);
            }
            return current;
        });

        long count = counter.counter().incrementAndGet();
        cleanupExpiredLocalCounters(nowMillis);
        return count <= limit;
    }

    private WindowKey buildWindowKey(String key, Duration window) {
        long windowSeconds = Math.max(1L, window.getSeconds());
        long nowSeconds = Instant.now().getEpochSecond();
        long windowStart = nowSeconds - (nowSeconds % windowSeconds);
        return new WindowKey(FIXED_WINDOW_PREFIX + ":" + windowSeconds + ":" + windowStart + ":" + key);
    }

    private void cleanupExpiredLocalCounters(long nowMillis) {
        if (localCounters.size() < LOCAL_CLEANUP_THRESHOLD) {
            return;
        }

        int removed = 0;
        Iterator<Map.Entry<String, LocalCounter>> iterator = localCounters.entrySet().iterator();
        while (iterator.hasNext() && removed < LOCAL_CLEANUP_BATCH) {
            Map.Entry<String, LocalCounter> entry = iterator.next();
            if (entry.getValue().expiresAtEpochMillis() <= nowMillis) {
                iterator.remove();
                removed++;
            }
        }
    }

    private void logRedisFailure(Exception e) {
        long now = System.currentTimeMillis();
        long previous = lastRedisErrorLogAt.get();
        if (now - previous >= REDIS_ERROR_LOG_INTERVAL_MS && lastRedisErrorLogAt.compareAndSet(previous, now)) {
            log.warn("Redis rate limiter unavailable; using local in-memory fallback.", e);
        } else {
            log.debug("Redis rate limiter unavailable; using local in-memory fallback. Reason: {}", e.getMessage());
        }
    }

    public String ipKey(String action, String ip) {
        return String.format("rl:%s:ip:%s", action, ip);
    }

    public String userKey(String action, Long userId) {
        return String.format("rl:%s:user:%s", action, userId);
    }

    private record WindowKey(String value) {
    }

    private record LocalCounter(AtomicLong counter, long expiresAtEpochMillis) {
    }
}
