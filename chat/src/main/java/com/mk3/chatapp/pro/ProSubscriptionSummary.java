package com.mk3.chatapp.pro;

import java.time.Instant;

public record ProSubscriptionSummary(
        boolean billingAvailable,
        boolean yearlyBillingEnabled,
        boolean canPurchase,
        boolean canManageBilling,
        boolean canChangePlan,
        boolean canResume,
        String status,
        String interval,
        boolean proActive,
        boolean showProBadge,
        boolean proBadgeVisible,
        long proBadgeRevision,
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
