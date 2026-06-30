package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.models.Ban;
import com.mk3.chatapp.services.BanCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BanCacheServiceImpl implements BanCacheService {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BAN_KEY_PREFIX = "ban:";
    private static final String USER_BAN_KEY_PREFIX = "user_ban:";
    private static final String IP_BAN_KEY_PREFIX = "ip_ban:";

    public void addBanEntry(Ban ban) {
        String banKey = BAN_KEY_PREFIX + ban.getId();
        String userBanKey = USER_BAN_KEY_PREFIX + ban.getUser().getId();

        redisTemplate.opsForValue().set(banKey, ban);

        redisTemplate.opsForValue().set(userBanKey, ban.getId());

        if (ban.getIpAddress() != null && !ban.getIpAddress().trim().isEmpty()) {
            String ipBanKey = IP_BAN_KEY_PREFIX + ban.getIpAddress();
            redisTemplate.opsForValue().set(ipBanKey, ban.getId());
        }

        if (ban.getExpiresAt() != null) {
            long ttlSeconds = Duration.between(Instant.now(), ban.getExpiresAt()).getSeconds();
            if (ttlSeconds > 0) {
                redisTemplate.expire(banKey, ttlSeconds, TimeUnit.SECONDS);
                redisTemplate.expire(userBanKey, ttlSeconds, TimeUnit.SECONDS);

                if (ban.getIpAddress() != null && !ban.getIpAddress().trim().isEmpty()) {
                    String ipBanKey = IP_BAN_KEY_PREFIX + ban.getIpAddress();
                    redisTemplate.expire(ipBanKey, ttlSeconds, TimeUnit.SECONDS);
                }
            }
        }
    }

    public boolean isUserBanned(Long userId) {
        String userBanKey = USER_BAN_KEY_PREFIX + userId;
        return redisTemplate.hasKey(userBanKey);
    }

    public boolean isIpBanned(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return false;
        }
        String ipBanKey = IP_BAN_KEY_PREFIX + ipAddress;
        return redisTemplate.hasKey(ipBanKey);
    }

    public Ban getBanByUserId(Long userId) {
        String userBanKey = USER_BAN_KEY_PREFIX + userId;
        Object banId = redisTemplate.opsForValue().get(userBanKey);

        if (banId != null) {
            String banKey = BAN_KEY_PREFIX + banId;
            return (Ban) redisTemplate.opsForValue().get(banKey);
        }

        return null;
    }

    public void removeBanEntry(Ban ban) {
        String banKey = BAN_KEY_PREFIX + ban.getId();
        String userBanKey = USER_BAN_KEY_PREFIX + ban.getUser().getId();

        redisTemplate.delete(banKey);
        redisTemplate.delete(userBanKey);

        if (ban.getIpAddress() != null && !ban.getIpAddress().trim().isEmpty()) {
            String ipBanKey = IP_BAN_KEY_PREFIX + ban.getIpAddress();
            redisTemplate.delete(ipBanKey);
        }
    }


    public void removeBanEntryByUserId(Long userId) {
        Ban ban = getBanByUserId(userId);
        if (ban != null) {
            removeBanEntry(ban);
        }
    }
}