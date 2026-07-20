package com.example.adsportalbe.repositories;

import com.example.adsportalbe.enums.PurchaseType;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    @Query("SELECT SUM(p.amountPaid) FROM PaymentReceipt p " +
            "WHERE p.paidAt BETWEEN :start AND :end AND p.purchaseType = :purchaseType")
    Double sumAmountPaidByPaidAtBetweenAndType(Instant start, Instant end, PurchaseType purchaseType);

    @Query("SELECT MONTH(p.paidAt) as month, SUM(p.amountPaid) as revenue " +
            "FROM PaymentReceipt p " +
            "WHERE YEAR(p.paidAt) = :year AND p.purchaseType = :purchaseType " +
            "GROUP BY MONTH(p.paidAt)")
    List<Object[]> findMonthlyRevenueByType(int year, PurchaseType purchaseType);

    @Query("SELECT DATE(p.paidAt) as date, SUM(p.amountPaid) as revenue " +
            "FROM PaymentReceipt p " +
            "WHERE p.paidAt BETWEEN :start AND :end AND p.purchaseType = :purchaseType " +
            "GROUP BY DATE(p.paidAt)")
    List<Object[]> findDailyRevenueForDateRangeByType(Instant start, Instant end, PurchaseType purchaseType);

    // CAPTURED only — REFUNDED receipts keep their paidAt and must not count as revenue
    @Query("SELECT SUM(p.amountPaid) FROM PaymentReceipt p " +
            "WHERE p.paidAt BETWEEN :start AND :end AND p.purchaseType = :purchaseType " +
            "AND p.status = 'CAPTURED'")
    Double sumCapturedAmountByPaidAtBetweenAndType(Instant start, Instant end, PurchaseType purchaseType);

    @Query("SELECT SUM(p.amountPaid) FROM PaymentReceipt p " +
            "WHERE p.purchaseType = :purchaseType AND p.status = 'CAPTURED'")
    Double sumCapturedAmountByType(PurchaseType purchaseType);

    @Query("SELECT DATE(p.paidAt) as date, SUM(p.amountPaid) as revenue " +
            "FROM PaymentReceipt p " +
            "WHERE p.paidAt BETWEEN :start AND :end AND p.purchaseType = :purchaseType " +
            "AND p.status = 'CAPTURED' " +
            "GROUP BY DATE(p.paidAt)")
    List<Object[]> findDailyCapturedRevenueForDateRangeByType(Instant start, Instant end, PurchaseType purchaseType);
}
