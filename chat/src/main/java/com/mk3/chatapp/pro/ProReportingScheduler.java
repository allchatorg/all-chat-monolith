package com.mk3.chatapp.pro;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProReportingScheduler {
    private final ProPaymentReportingService reporting;

    @EventListener(ApplicationReadyEvent.class)
    public void activate() {
        reporting.initialize();
    }

    @Scheduled(scheduler = "proReportingTaskScheduler", fixedDelayString = "${app.pro.reporting-delay-ms:60000}",
            initialDelayString = "${app.pro.reporting-delay-ms:60000}")
    public void reconcile() {
        try {
            reporting.reconcilePage();
        } catch (Exception e) {
            log.warn("Subscription reporting reconciliation failed ({})", e.getClass().getSimpleName());
            try {
                reporting.markSynchronizationFailed();
            } catch (Exception stateFailure) {
                log.warn("Subscription reporting failure state could not be saved ({})", stateFailure.getClass().getSimpleName());
            }
        }
    }
}
