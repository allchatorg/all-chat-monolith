package com.mk3.chatapp.services.schedulers;

import com.mk3.chatapp.configs.CommunityNightProperties;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.NotificationRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * Sends the weekly community-night reminder at the configured local hour in
 * every user's own time zone. Runs every 15 minutes (the finest real UTC
 * offset granularity), finds the zones where it is currently the configured
 * day/hour, and notifies users in those zones who haven't been notified this week.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "community-night.reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CommunityNightReminderScheduler {

    private static final int TICK_MINUTES = 15;
    private static final Duration DEDUP_WINDOW = Duration.ofDays(6);

    private final CommunityNightProperties properties;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "${community-night.reminder.cron:0 0/15 * * * ?}")
    public void sendDueReminders() {
        Instant now = Instant.now();
        boolean debug = properties.isDebug();
        Set<String> dueZones = debug ? ZoneId.getAvailableZoneIds() : findDueZones(now);
        if (dueZones.isEmpty()) {
            return;
        }

        List<User> candidates = userRepository.findActiveUsersInTimeZones(dueZones, properties.getFallbackZone());
        if (candidates.isEmpty()) {
            return;
        }

        Set<Long> alreadyNotified = debug ? Set.of() : notificationRepository.findUserIdsNotifiedSince(
                NotificationType.COMMUNITY_NIGHT_REMINDER, now.minus(DEDUP_WINDOW));

        int sent = 0;
        for (User user : candidates) {
            if (alreadyNotified.contains(user.getId())) {
                continue;
            }
            try {
                // One transaction per user so a single failure doesn't roll back the batch.
                notificationService.createAndSend(user, NotificationType.COMMUNITY_NIGHT_REMINDER,
                        properties.getTitle(), properties.getBody(), null, null, null);
                sent++;
            } catch (Exception e) {
                log.warn("Failed to send community-night reminder to user {}", user.getId(), e);
            }
        }
        log.info("Community-night reminder{}: {} zone(s) due, {} user(s) notified",
                debug ? " [DEBUG]" : "", dueZones.size(), sent);
    }

    private Set<String> findDueZones(Instant now) {
        return ZoneId.getAvailableZoneIds().stream()
                .filter(id -> {
                    ZonedDateTime local = now.atZone(ZoneId.of(id));
                    return local.getDayOfWeek() == properties.getDay()
                            && local.getHour() == properties.getHour()
                            && local.getMinute() < TICK_MINUTES;
                })
                .collect(java.util.stream.Collectors.toSet());
    }
}
