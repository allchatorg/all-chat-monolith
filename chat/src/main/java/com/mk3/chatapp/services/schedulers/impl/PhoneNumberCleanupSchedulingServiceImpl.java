package com.mk3.chatapp.services.schedulers.impl;

import com.mk3.chatapp.jobs.PhoneNumberCleanupJob;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.schedulers.GenericSchedulingService;
import com.mk3.chatapp.services.schedulers.PhoneNumberCleanupSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneNumberCleanupSchedulingServiceImpl implements PhoneNumberCleanupSchedulingService {

    private final GenericSchedulingService schedulingService;

    public void schedulePhoneNumberCleanup(User user, Instant validationExpiryTime) {
        schedulingService.scheduleJob(
                PhoneNumberCleanupJob.class,
                "phone-cleanup-jobs",
                "phone-cleanup-triggers",
                "Phone Number Cleanup Job",
                Map.of("userId", user.getId()),
                validationExpiryTime
        );
    }

    public void cancelPhoneNumberCleanupJob(User user) {
        schedulingService.cancelJob(
                user.getId().toString(),
                "phone-cleanup-jobs"
        );
    }
}
