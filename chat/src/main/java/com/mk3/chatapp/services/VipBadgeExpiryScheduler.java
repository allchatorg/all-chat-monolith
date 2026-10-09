package com.mk3.chatapp.services;

import com.mk3.chatapp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Local expiry cannot wait behind remote billing calls or a failed Stripe reconciliation. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VipBadgeExpiryScheduler {
    private final UserRepository users;
    private final VipBadgeService badges;

    @Bean(name = "vipBadgeExpiryTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler scheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("vip-badge-expiry-");
        return scheduler;
    }

    @Scheduled(fixedDelayString = "${app.vip.badge-expiry-delay-ms:30000}",
            initialDelayString = "${app.vip.badge-expiry-delay-ms:30000}", scheduler = "vipBadgeExpiryTaskScheduler")
    public void expireBadges() {
        long afterId = 0;
        Instant now = Instant.now();
        while (true) {
            var ids = users.findExpiredVipBadgeUserIds(now, afterId, PageRequest.of(0, 200));
            if (ids.isEmpty()) return;
            for (Long userId : ids) {
                try {
                    badges.expireBadge(userId);
                } catch (Exception e) {
                    log.warn("Could not expire VIP badge for user {} ({})", userId, e.getClass().getSimpleName());
                }
            }
            // Advance even after an individual failure so it cannot starve other users.
            afterId = ids.get(ids.size() - 1);
        }
    }
}
