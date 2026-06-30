package com.mk3.chatapp.tasks;

import com.mk3.chatapp.services.MessagingAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessagingAvailabilityRefreshTask {
    private final MessagingAvailabilityService messagingAvailabilityService;

    @Scheduled(cron = "*/3 * * * * *")
    public void refreshMessagingAvailability() {
        messagingAvailabilityService.refreshAndBroadcastIfChanged();
    }
}
