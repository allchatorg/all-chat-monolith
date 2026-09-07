package com.example.adsportalbe.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PurchaseEmailSchedulingConfig {

    /** Slow SMTP must not delay the existing chat heartbeat and ad reconciliation jobs. */
    @Bean(name = "purchaseEmailTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler purchaseEmailTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("purchase-email-");
        return scheduler;
    }
}
