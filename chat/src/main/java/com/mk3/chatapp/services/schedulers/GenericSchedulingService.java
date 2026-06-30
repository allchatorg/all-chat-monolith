package com.mk3.chatapp.services.schedulers;


import org.quartz.Job;

import java.time.Instant;
import java.util.Map;

public interface GenericSchedulingService {

    /**
     * Schedule a Quartz job.
     *
     * @param jobClass       The job class to execute.
     * @param jobGroup       The Quartz job group.
     * @param triggerGroup   The Quartz trigger group.
     * @param jobDescription Description of the job.
     * @param jobDataMap     Data to pass to the job.
     * @param scheduledTime  When the job should run (null to skip scheduling).
     */
    void scheduleJob(
            Class<? extends Job> jobClass,
            String jobGroup,
            String triggerGroup,
            String jobDescription,
            Map<String, Object> jobDataMap,
            Instant scheduledTime
    );

    /**
     * Cancel a scheduled job by its ID and group.
     *
     * @param jobId    The job ID.
     * @param jobGroup The Quartz job group.
     */
    void cancelJob(String jobId, String jobGroup);
}

