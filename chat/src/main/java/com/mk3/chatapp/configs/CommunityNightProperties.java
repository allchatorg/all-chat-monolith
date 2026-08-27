package com.mk3.chatapp.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;

/**
 * Weekly community-night reminder. Fires at {@code hour}:00 on {@code day} in
 * each user's own time zone (users without a stored zone use {@code fallbackZone}).
 */
@Component
@ConfigurationProperties(prefix = "community-night.reminder")
@Getter
@Setter
public class CommunityNightProperties {
    private boolean enabled = true;
    /** Scheduler tick; must be at least as fine as {@code TICK_MINUTES} in the scheduler. */
    private String cron = "0 0/15 * * * ?";
    /** Dev only: ignore day/hour/zone matching and weekly dedup, notify every active user on every tick. */
    private boolean debug = false;
    private DayOfWeek day = DayOfWeek.THURSDAY;
    private int hour = 17;
    private String fallbackZone = "UTC";
    private String title = "Community nights are this weekend!";
    private String body = "Community nights are Friday and Saturday from 5 PM to midnight. See you there!";
}
