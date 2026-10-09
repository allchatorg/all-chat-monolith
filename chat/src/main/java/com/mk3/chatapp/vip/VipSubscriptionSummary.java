package com.mk3.chatapp.vip;

import java.time.Instant;

public record VipSubscriptionSummary(
        boolean billingAvailable,
        boolean yearlyBillingEnabled,
        boolean canPurchase,
        boolean canManageBilling,
        boolean canChangePlan,
        boolean canResume,
        String status,
        String interval,
        boolean vipActive,
        boolean showVipBadge,
        boolean vipBadgeVisible,
        long vipBadgeRevision,
        Instant currentPeriodEnd,
        Instant paidThrough,
        boolean cancelAtPeriodEnd,
        String scheduledInterval,
        Instant scheduledChangeAt,
        boolean checkoutPending,
        String checkoutInterval,
        boolean canContinueCheckout,
        String pendingInterval,
        Instant pendingUpdateExpiresAt,
        String pendingInvoiceId,
        String renewalPaymentMethodId) {
}
