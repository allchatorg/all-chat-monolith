package com.mk3.chatapp.services.schedulers;

import com.mk3.chatapp.models.identity.User;

import java.time.Instant;

public interface PhoneNumberCleanupSchedulingService {
    void schedulePhoneNumberCleanup(User user, Instant validationExpiryTime);

    void cancelPhoneNumberCleanupJob(User user);
}
