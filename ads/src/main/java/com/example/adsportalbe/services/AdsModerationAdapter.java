package com.example.adsportalbe.services;

import com.mk3.chatapp.services.AdsModerationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * In-process implementation of the chat module's {@link AdsModerationPort},
 * mirroring {@link AdServingAdapter}. Keeps the dependency direction
 * {@code ads -> chat}.
 */
@Service
@RequiredArgsConstructor
public class AdsModerationAdapter implements AdsModerationPort {

    private final AdService adService;

    @Override
    public PendingAdRefundResult refundPendingAdPurchases(Long userId) {
        AdService.PendingAdRefundOutcome outcome = adService.refundPendingAdsForUser(userId);
        return new PendingAdRefundResult(
                outcome.attempted(),
                outcome.refunded(),
                outcome.totalRefunded(),
                outcome.currency());
    }
}
