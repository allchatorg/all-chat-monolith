package com.mk3.chatapp.models.vip;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One durable billing projection per account; Stripe remains the billing authority. */
@Entity
@Table(name = "vip_subscription", indexes = @Index(name = "idx_vip_subscription_reconcile", columnList = "last_reconciled_at"))
@Getter
@Setter
@NoArgsConstructor
public class VipSubscription {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;
    @Column(name = "stripe_schedule_id")
    private String stripeScheduleId;
    @Column(name = "billing_interval", length = 16)
    private String billingInterval;
    @Column(name = "status", nullable = false, length = 32)
    private String status = "NONE";
    @Column(name = "paid_through")
    private Instant paidThrough;
    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;
    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;
    @Column(name = "scheduled_interval", length = 16)
    private String scheduledInterval;
    @Column(name = "scheduled_change_at")
    private Instant scheduledChangeAt;
    @Column(name = "checkout_session_id", unique = true)
    private String checkoutSessionId;
    @Column(name = "checkout_attempt_id", length = 36)
    private String checkoutAttemptId;
    @Column(name = "checkout_interval", length = 16)
    private String checkoutInterval;
    @Column(name = "checkout_expires_at")
    private Instant checkoutExpiresAt;
    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;
}
