package com.mk3.chatapp.pro;

import com.mk3.chatapp.repositories.ProSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProReconciliationScheduler {
    private final ProSubscriptionRepository subscriptions;
    private final ProBillingService billing;

    @Scheduled(scheduler = "proReconciliationTaskScheduler", fixedDelayString = "${app.pro.reconciliation-delay-ms:60000}",
            initialDelayString = "${app.pro.reconciliation-delay-ms:60000}")
    public void reconcile() {
        for (Long userId : subscriptions.findDueForReconciliation(Instant.now().minusSeconds(60), PageRequest.of(0, 50))) {
            try {
                billing.reconcileAccount(userId);
            } catch (Exception e) {
                log.warn("Pro reconciliation could not complete for user {} ({})", userId, e.getClass().getSimpleName());
            }
        }
    }
}
