package com.example.adsportalbe.services;

import com.example.adsportalbe.repositories.PurchaseEmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseEmailDeliveryService {

    private final PurchaseEmailOutboxRepository outboxRepository;
    private final MailService mailService;

    /**
     * The row lock prevents concurrent application instances from sending the same event. SMTP timeouts bound
     * the network wait. A crash after SMTP acceptance but before commit can still redeliver: delivery is at least once.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliverNext() {
        var pending = outboxRepository.lockNextDue();
        if (pending.isEmpty()) {
            return false;
        }
        var email = pending.get();
        email.setAttempts(Math.min(email.getAttempts(), Integer.MAX_VALUE - 1) + 1);
        try {
            mailService.sendPurchaseUpdateEmail(email.getRecipientEmail(), email.getTitle(), email.getBody(),
                    email.getPurchaseReference(), email.getDetailsPath());
            email.setDeliveredAt(Instant.now());
            email.setNextAttemptAt(null);
            email.setLastFailureCode(null);
            log.info("Purchase email accepted by SMTP: outboxId={}, attempt={}", email.getId(), email.getAttempts());
        } catch (Exception exception) {
            long backoffSeconds = Math.min(21_600L, 30L * (1L << Math.min(email.getAttempts() - 1, 10)));
            email.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
            email.setLastFailureCode(exception.getClass().getSimpleName());
            log.warn("Purchase email delivery failed: outboxId={}, attempt={}, category={}, retryAt={}",
                    email.getId(), email.getAttempts(), email.getLastFailureCode(), email.getNextAttemptAt());
        }
        return true;
    }
}
