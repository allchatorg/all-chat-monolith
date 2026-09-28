package com.mk3.chatapp.pro;

import com.mk3.chatapp.models.pro.ProReportingState;
import com.mk3.chatapp.models.pro.ProSubscriptionPayment;
import com.mk3.chatapp.repositories.ProReportingStateRepository;
import com.mk3.chatapp.repositories.ProSubscriptionPaymentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Dashboard reads are database-only; Stripe recovery runs separately. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProStatisticsService {
    private final ProReportingStateRepository states;
    private final ProSubscriptionPaymentRepository payments;
    private final ProConfiguration config;
    private final EntityManager entityManager;

    public ProStatisticsResponse statistics(int days) {
        if (!Set.of(7, 30, 90).contains(days)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be 7, 30, or 90");
        }
        ProReportingState state = state();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.minusDays(days - 1);
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        Map<LocalDate, List<ProSubscriptionPayment>> byDate = payments
                .findByPaidAtGreaterThanEqualAndPaidAtLessThan(first.atStartOfDay(zone).toInstant(), end).stream()
                .collect(Collectors.groupingBy(p -> p.getPaidAt().atZone(zone).toLocalDate()));
        var daily = new ArrayList<ProStatisticsResponse.Daily>();
        for (LocalDate date = first; !date.isAfter(today); date = date.plusDays(1)) {
            List<ProSubscriptionPayment> rows = byDate.getOrDefault(date, List.of());
            long initial = rows.stream().filter(p -> "subscription_create".equals(p.getBillingReason())).count();
            long renewals = rows.stream().filter(p -> "subscription_cycle".equals(p.getBillingReason())).count();
            daily.add(new ProStatisticsResponse.Daily(date,
                    amount(rows.stream().mapToLong(p -> p.getPaidCents() - p.getRefundedCents()).sum()),
                    initial, renewals, rows.size() - initial - renewals));
        }
        Instant yesterday = today.minusDays(1).atStartOfDay(zone).toInstant();
        var revenue = new ProStatisticsResponse.Revenue(
                amount(payments.netCentsBetween(today.atStartOfDay(zone).toInstant(), end)),
                amount(payments.netCentsBetween(yesterday, today.atStartOfDay(zone).toInstant())),
                amount(payments.netCentsTotal()));
        return new ProStatisticsResponse(synchronization(state), memberships(), revenue, daily);
    }

    public BigDecimal revenueBetween(LocalDate startInclusive, LocalDate endExclusive) {
        ZoneId zone = ZoneId.systemDefault();
        Instant end = endExclusive.atStartOfDay(zone).toInstant();
        return amount(payments.netCentsBetween(startInclusive.atStartOfDay(zone).toInstant(), end));
    }

    public ProStatisticsResponse.Synchronization synchronization() {
        return synchronization(state());
    }

    private ProStatisticsResponse.Synchronization synchronization(ProReportingState state) {
        String status;
        if (state.isIncomplete()) status = "INCOMPLETE";
        else if (!config.hasApiKey() || state.isSynchronizationFailed()) status = "UNAVAILABLE";
        else if (!state.isSynchronizedOnce()
                || state.getLastSynchronizedAt().isBefore(Instant.now().minusSeconds(600))) status = "CATCHING_UP";
        else status = "CURRENT";
        return new ProStatisticsResponse.Synchronization(status,
                state.isSynchronizedOnce() ? state.getLastSynchronizedAt() : null);
    }

    private ProStatisticsResponse.Memberships memberships() {
        Object[] counts = entityManager.createQuery("""
                select coalesce(sum(case when u.proPaidThrough > :now then 1 else 0 end), 0),
                       coalesce(sum(case when u.proPaidThrough > :now and p.billingInterval = 'MONTHLY' then 1 else 0 end), 0),
                       coalesce(sum(case when u.proPaidThrough > :now and p.billingInterval = 'YEARLY' then 1 else 0 end), 0),
                       coalesce(sum(case when u.proPaidThrough > :now and p.cancelAtPeriodEnd = true then 1 else 0 end), 0),
                       coalesce(sum(case when p.status in ('PAST_DUE', 'UNPAID') then 1 else 0 end), 0)
                from User u left join ProSubscription p on p.userId = u.id
                where u.deleted = false or u.deleted is null
                """, Object[].class).setParameter("now", Instant.now()).getSingleResult();
        return new ProStatisticsResponse.Memberships(((Number) counts[0]).longValue(), ((Number) counts[1]).longValue(),
                ((Number) counts[2]).longValue(), ((Number) counts[3]).longValue(), ((Number) counts[4]).longValue());
    }

    private ProReportingState state() {
        return states.findById(ProReportingState.SINGLETON_ID).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Subscription reporting is starting"));
    }

    private static BigDecimal amount(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
