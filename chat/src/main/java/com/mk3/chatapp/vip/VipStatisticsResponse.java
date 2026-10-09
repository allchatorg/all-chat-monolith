package com.mk3.chatapp.vip;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record VipStatisticsResponse(Synchronization synchronization,
        Memberships memberships, Revenue revenue, List<Daily> daily) {
    public record Synchronization(String status, Instant lastSynchronizedAt) { }
    public record Memberships(long active, long monthly, long yearly, long scheduledCancellations, long paymentIssues) { }
    public record Revenue(BigDecimal today, BigDecimal yesterday, BigDecimal total) { }
    public record Daily(LocalDate date, BigDecimal revenue, long initialPayments, long renewalPayments, long otherPayments) { }
}
