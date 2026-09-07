package com.example.adsportalbe.repositories;

import com.example.adsportalbe.models.payment.PurchaseEmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PurchaseEmailOutboxRepository extends JpaRepository<PurchaseEmailOutbox, Long> {

    boolean existsByEventKey(String eventKey);

    /**
     * Preserve each purchase's event order, including while an earlier email is backing off or locked by
     * another worker. Unrelated purchases can still proceed. The caller holds the selected row lock until
     * send and outcome persistence finish in an independent transaction.
     */
    @Query(value = """
            SELECT pending.* FROM purchase_email_outbox pending
            WHERE pending.delivered_at IS NULL AND pending.next_attempt_at <= CURRENT_TIMESTAMP
              AND NOT EXISTS (
                SELECT 1 FROM purchase_email_outbox earlier
                WHERE earlier.purchase_reference = pending.purchase_reference
                  AND earlier.id < pending.id AND earlier.delivered_at IS NULL
              )
            ORDER BY pending.next_attempt_at, pending.id
            LIMIT 1 FOR UPDATE OF pending SKIP LOCKED
            """, nativeQuery = true)
    Optional<PurchaseEmailOutbox> lockNextDue();
}
