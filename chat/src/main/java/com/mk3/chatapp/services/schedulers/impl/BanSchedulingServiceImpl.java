package com.mk3.chatapp.services.schedulers.impl;

import com.mk3.chatapp.jobs.UnbanJob;
import com.mk3.chatapp.models.Ban;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.schedulers.BanSchedulingService;
import com.mk3.chatapp.services.schedulers.GenericSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BanSchedulingServiceImpl implements BanSchedulingService {

    private final GenericSchedulingService schedulingService;

    public void scheduleUnban(Ban ban) {
        schedulingService.scheduleJob(
                UnbanJob.class,
                "unban-jobs",
                "unban-triggers",
                "Revoke Ban Job",
                Map.of("userId", ban.getUser().getId()),
                ban.getExpiresAt()
        );
    }

    public void cancelUnbanJob(User user) {
        schedulingService.cancelJob(
                user.getId().toString(),
                "unban-jobs"
        );
    }
}
