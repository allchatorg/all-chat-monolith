package com.mk3.chatapp.jobs;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class PhoneNumberCleanupJob implements Job {

    private final UserService userService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long userId = context.getJobDetail().getJobDataMap().getLong("userId");

        try {
            User user = userService.findById(userId);

            if (user != null && user.getPhoneNumber() != null && Objects.isNull(user.getPhoneNumberVerificationDate())) {
                log.info("Removing unverified phone number for user ID: {}", userId);
                userService.removeUserPhoneNumber(user);
                log.info("Successfully removed phone number for user ID: {}", userId);
            } else {
                log.info("Phone number already verified or removed for user ID: {}", userId);
            }
        } catch (Exception e) {
            log.error("Error removing phone number for user ID: {}", userId, e);
            throw new JobExecutionException("Failed to remove phone number", e);
        }
    }
}
