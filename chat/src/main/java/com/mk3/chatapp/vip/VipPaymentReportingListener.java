package com.mk3.chatapp.vip;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.stripe.exception.StripeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class VipPaymentReportingListener {
    private final VipPaymentReportingService reporting;
    private final TaskExecutor executor;

    public VipPaymentReportingListener(VipPaymentReportingService reporting,
            @Qualifier("vipReportingWebhookExecutor") TaskExecutor executor) {
        this.reporting = reporting;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentEvent(VipPaymentReportingEvent event) {
        try {
            executor.execute(() -> record(event));
        } catch (RuntimeException e) {
            // Queue saturation/shutdown must not fail a committed billing webhook or wait for a lock.
            // The persistent event scanner independently recovers any rejected or lost queued work.
            log.warn("Subscription reporting dispatch deferred to event recovery ({})", e.getClass().getSimpleName());
        }
    }

    private void record(VipPaymentReportingEvent event) {
        try {
            var object = JsonNodeFactory.instance.objectNode()
                    .put("id", event.objectId()).put("charge", event.chargeId());
            reporting.handleEvent(event.type(), object);
        } catch (StripeException | RuntimeException e) {
            log.warn("Subscription webhook reporting deferred to event recovery ({})", e.getClass().getSimpleName());
            try {
                reporting.markSynchronizationFailed();
            } catch (RuntimeException stateFailure) {
                log.warn("Subscription reporting failure state could not be saved ({})", stateFailure.getClass().getSimpleName());
            }
        }
    }
}
