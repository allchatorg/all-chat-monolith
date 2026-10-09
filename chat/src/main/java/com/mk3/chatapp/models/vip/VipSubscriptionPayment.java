package com.mk3.chatapp.models.vip;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.Instant;

/** Financial history deliberately has no cascading relationship to an account. */
@Entity
@Table(name = "vip_subscription_payment", indexes = @Index(name = "idx_vip_payment_paid_at", columnList = "paid_at"))
@Check(constraints = "paid_cents > 0 and refunded_cents >= 0 and refunded_cents <= paid_cents and currency = 'usd'")
@Getter
@Setter
@NoArgsConstructor
public class VipSubscriptionPayment {
    @Id
    @Column(name = "stripe_invoice_id")
    private String stripeInvoiceId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;
    @Column(name = "stripe_subscription_id", nullable = false)
    private String stripeSubscriptionId;
    @Column(name = "stripe_charge_id", nullable = false)
    private String stripeChargeId;
    @Column(name = "paid_cents", nullable = false)
    private long paidCents;
    @Column(name = "refunded_cents", nullable = false)
    private long refundedCents;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;
    @Column(name = "billing_reason", nullable = false, length = 64)
    private String billingReason;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
