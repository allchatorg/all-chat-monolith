package com.example.adsportalbe.repositories;

import com.example.adsportalbe.models.payment.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    @Query("SELECT SUM(p.amountPaid) FROM PaymentReceipt p WHERE p.paidAt BETWEEN :start AND :end")
    Double sumAmountPaidByPaidAtBetween(Instant start, Instant end);

    @Query("SELECT MONTH(p.paidAt) as month, SUM(p.amountPaid) as revenue " +
            "FROM PaymentReceipt p " +
            "WHERE YEAR(p.paidAt) = :year " +
            "GROUP BY MONTH(p.paidAt)")
    List<Object[]> findMonthlyRevenue(int year);

    @Query("SELECT DATE(p.paidAt) as date, SUM(p.amountPaid) as revenue " +
            "FROM PaymentReceipt p " +
            "WHERE p.paidAt BETWEEN :start AND :end " +
            "GROUP BY DATE(p.paidAt)")
    List<Object[]> findDailyRevenueForDateRange(Instant start, Instant end);
}
