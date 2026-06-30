package com.mk3.chatapp.jobs;

import com.mk3.chatapp.services.BanService;
import com.mk3.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UnbanJob extends QuartzJobBean {

    private final BanService banService;
    private final UserService userService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        long userId = context.getJobDetail().getJobDataMap().getLong("userId");
        log.info("Executing UnbanJob for user ID: {}", userId);

        try {
            var userToUnban = userService.findById(userId);
            banService.systemRevokeBan(userToUnban);
            log.info("Successfully revoked ban for user ID: {}", userId);
        } catch (Exception e) {
            log.error("Failed to execute UnbanJob for user ID: {}. Reason: {}", userId, e.getMessage());
        }
    }
}