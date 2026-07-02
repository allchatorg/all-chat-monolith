package com.mk3.chatapp.services;

/**
 * SPI for ads-side moderation side effects triggered from chat (e.g. refunding
 * a user's pending ad purchases when they are permanently banned).
 *
 * <p>Defined in the chat module so chat depends only on this interface — the
 * dependency direction stays {@code ads -> chat} (chat never imports ads),
 * avoiding a module cycle. The ads module supplies the bean.
 */
public interface AdsModerationPort {

    /**
     * Cancels the payment authorization for every pending ad purchase (a
     * SUBMITTED ad whose payment is authorized but not yet captured) owned by
     * the given user, releasing the full amount back to them.
     *
     * <p>Per-ad failures are caught and logged by the implementation so one
     * failed refund never aborts the others.
     */
    PendingAdRefundResult refundPendingAdPurchases(Long userId);

    record PendingAdRefundResult(int attempted, int refunded, double totalRefunded, String currency) {}
}
