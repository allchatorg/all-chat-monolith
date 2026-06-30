package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.MessagingAvailabilityDTO;

public interface MessagingAvailabilityService {
    MessagingAvailabilityDTO getCurrentAvailability();

    MessagingAvailabilityDTO refreshAndBroadcastIfChanged();
}
