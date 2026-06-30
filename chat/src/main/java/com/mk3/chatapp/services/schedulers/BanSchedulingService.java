package com.mk3.chatapp.services.schedulers;

import com.mk3.chatapp.models.Ban;
import com.mk3.chatapp.models.identity.User;

public interface BanSchedulingService {
    void scheduleUnban(Ban ban);

    void cancelUnbanJob(User user);
}
