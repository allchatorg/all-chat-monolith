package com.mk3.chatapp.services.schedulers;

import com.mk3.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CronJobs {

    private final UserService userService;

    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyTask() {
        userService.deleteStaleAccounts();
    }
}