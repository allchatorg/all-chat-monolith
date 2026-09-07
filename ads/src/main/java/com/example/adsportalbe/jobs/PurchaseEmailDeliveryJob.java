package com.example.adsportalbe.jobs;

import com.example.adsportalbe.services.PurchaseEmailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.purchase-email.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PurchaseEmailDeliveryJob {

    private final PurchaseEmailDeliveryService deliveryService;

    @Value("${app.purchase-email.batch-size:10}")
    private int batchSize;

    @Scheduled(scheduler = "purchaseEmailTaskScheduler",
            fixedDelayString = "${app.purchase-email.fixed-delay-ms:5000}",
            initialDelayString = "${app.purchase-email.initial-delay-ms:15000}")
    public void deliverPending() {
        try {
            for (int processed = 0; processed < Math.clamp(batchSize, 1, 100); processed++) {
                if (!deliveryService.deliverNext()) {
                    break;
                }
            }
        } catch (Exception exception) {
            // A failed DB transaction releases its row lock. The committed outbox survives for the next run.
            log.error("Purchase email worker could not persist delivery progress: category={}",
                    exception.getClass().getSimpleName());
        }
    }
}
