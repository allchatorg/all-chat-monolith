package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.pro.ProSubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProSubscriptionPaymentRepository extends JpaRepository<ProSubscriptionPayment, String> {
    @Query("select coalesce(sum(p.paidCents - p.refundedCents), 0) from ProSubscriptionPayment p")
    long netCentsTotal();

    @Query("select coalesce(sum(p.paidCents - p.refundedCents), 0) from ProSubscriptionPayment p " +
            "where p.paidAt >= :start and p.paidAt < :end")
    long netCentsBetween(@Param("start") Instant start, @Param("end") Instant end);

    List<ProSubscriptionPayment> findByPaidAtGreaterThanEqualAndPaidAtLessThan(Instant start, Instant end);
}
