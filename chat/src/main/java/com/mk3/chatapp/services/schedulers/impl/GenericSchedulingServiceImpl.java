package com.mk3.chatapp.services.schedulers.impl;

import com.mk3.chatapp.services.schedulers.GenericSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenericSchedulingServiceImpl implements GenericSchedulingService {

    private final Scheduler scheduler;

    public void scheduleJob(
            Class<? extends Job> jobClass,
            String jobGroup,
            String triggerGroup,
            String jobDescription,
            Map<String, Object> jobDataMap,
            Instant scheduledTime
    ) {
        try {
            String jobId = generateJobId(jobDataMap);
            JobDetail jobDetail = buildJobDetail(jobClass, jobId, jobGroup, jobDescription, jobDataMap);

            if (scheduledTime == null) {
                log.warn("No scheduled time provided for job: {}", jobId);
                return;
            }

            Instant now = Instant.now();
            Date fireTime = Date.from(scheduledTime);

            Trigger trigger;

            if (scheduledTime.isBefore(now)) {
                log.info("Scheduled time already passed for job: {}, firing immediately", jobId);
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobId, triggerGroup)
                        .withDescription("Immediate " + jobDescription)
                        .startNow()
                        .build();
            } else {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobId, triggerGroup)
                        .withDescription("Scheduled " + jobDescription)
                        .startAt(fireTime)
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withMisfireHandlingInstructionFireNow())
                        .build();
                log.info("Scheduled job: {} at {}", jobId, scheduledTime);
            }

            scheduler.scheduleJob(jobDetail, trigger);

        } catch (SchedulerException e) {
            log.error("Error scheduling job", e);
        }
    }

    public void cancelJob(String jobId, String jobGroup) {
        try {
            JobKey jobKey = new JobKey(jobId, jobGroup);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("Cancelled scheduled job: {}", jobId);
            }
        } catch (SchedulerException e) {
            log.error("Error cancelling scheduled job: {}", jobId, e);
        }
    }

    private JobDetail buildJobDetail(
            Class<? extends Job> jobClass,
            String jobId,
            String jobGroup,
            String description,
            Map<String, Object> data
    ) {
        JobDataMap jobDataMap = new JobDataMap(data);

        return JobBuilder.newJob(jobClass)
                .withIdentity(jobId, jobGroup)
                .withDescription(description)
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }

    private String generateJobId(Map<String, Object> jobDataMap) {
        // Generate a consistent ID from the job data
        return jobDataMap.values().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + "-" + b)
                .orElse("job-" + System.currentTimeMillis());
    }
}