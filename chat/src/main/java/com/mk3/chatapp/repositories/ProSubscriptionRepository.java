package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.pro.ProSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProSubscriptionRepository extends JpaRepository<ProSubscription, Long> {
    @Query("select p.userId from ProSubscription p where " +
            "(p.stripeSubscriptionId is not null or p.checkoutAttemptId is not null) and " +
            "(p.lastReconciledAt is null or p.lastReconciledAt < :before) " +
            "order by p.lastReconciledAt asc nulls first")
    List<Long> findDueForReconciliation(@Param("before") Instant before, Pageable pageable);
}
