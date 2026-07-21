package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.enums.AdStatus;
import com.example.adsportalbe.mappers.AdMapper;
import com.example.adsportalbe.models.ad.Ad;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import com.example.adsportalbe.repositories.AdFormatRepository;
import com.example.adsportalbe.repositories.AdRepository;
import com.example.adsportalbe.repositories.PaymentReceiptRepository;
import com.example.adsportalbe.services.AdCacheService;
import com.example.adsportalbe.services.AdService;
import com.example.adsportalbe.services.MailService;
import com.example.adsportalbe.services.PaymentService;
import com.stripe.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class AdServiceImplRefundTest {

    @Mock private AdRepository adRepository;
    @Mock private PaymentReceiptRepository paymentReceiptRepository;
    @Mock private AdFormatRepository adFormatRepository;
    @Mock private PaymentService paymentService;
    @Mock private MailService mailService;
    @Mock private AdCacheService adCacheService;
    @Mock private AdMapper adMapper;

    @InjectMocks private AdServiceImpl adService;

    private static Ad pendingAd(long id, String paymentIntentId, double amount) {
        Ad ad = Ad.builder()
                .id(id)
                .title("Ad " + id)
                .status(AdStatus.PENDING)
                .totalCost(amount)
                .build();
        PaymentReceipt receipt = PaymentReceipt.builder()
                .stripePaymentIntentId(paymentIntentId)
                .status("AUTHORIZED")
                .currency("USD")
                .amountPaid(amount)
                .ad(ad)
                .build();
        ad.setReceipt(receipt);
        return ad;
    }

    @Test
    void refundsAllPendingAdsAndMarksThemRejected() throws Exception {
        Ad first = pendingAd(1L, "pi_1", 20.0);
        Ad second = pendingAd(2L, "pi_2", 25.0);
        when(adRepository.findPendingRefundableAdsByOwnerId(42L)).thenReturn(List.of(first, second));

        AdService.PendingAdRefundOutcome outcome = adService.refundPendingAdsForUser(42L);

        assertThat(outcome.attempted()).isEqualTo(2);
        assertThat(outcome.refunded()).isEqualTo(2);
        assertThat(outcome.totalRefunded()).isEqualTo(45.0);
        assertThat(outcome.currency()).isEqualTo("USD");
        verify(paymentService).cancelPaymentAuthorization("pi_1");
        verify(paymentService).cancelPaymentAuthorization("pi_2");
        for (Ad ad : List.of(first, second)) {
            assertThat(ad.getStatus()).isEqualTo(AdStatus.REJECTED);
            assertThat(ad.getRejectionReason()).contains("permanently banned");
            assertThat(ad.getReceipt().getStatus()).isEqualTo("CANCELLED");
            verify(adRepository).save(ad);
        }
        verifyNoInteractions(mailService);
    }

    @Test
    void oneFailedStripeCancelDoesNotAbortTheOthers() throws Exception {
        Ad failing = pendingAd(1L, "pi_fail", 20.0);
        Ad succeeding = pendingAd(2L, "pi_ok", 25.0);
        when(adRepository.findPendingRefundableAdsByOwnerId(42L)).thenReturn(List.of(failing, succeeding));
        doThrow(new ApiException("stripe down", null, null, 500, null))
                .when(paymentService).cancelPaymentAuthorization("pi_fail");

        AdService.PendingAdRefundOutcome outcome = adService.refundPendingAdsForUser(42L);

        assertThat(outcome.attempted()).isEqualTo(2);
        assertThat(outcome.refunded()).isEqualTo(1);
        assertThat(outcome.totalRefunded()).isEqualTo(25.0);
        assertThat(failing.getStatus()).isEqualTo(AdStatus.PENDING);
        assertThat(failing.getReceipt().getStatus()).isEqualTo("AUTHORIZED");
        verify(adRepository, never()).save(failing);
        assertThat(succeeding.getStatus()).isEqualTo(AdStatus.REJECTED);
        assertThat(succeeding.getReceipt().getStatus()).isEqualTo("CANCELLED");
        verify(adRepository).save(succeeding);
    }

    @Test
    void noPendingAdsMeansNoStripeCalls() throws Exception {
        when(adRepository.findPendingRefundableAdsByOwnerId(42L)).thenReturn(List.of());

        AdService.PendingAdRefundOutcome outcome = adService.refundPendingAdsForUser(42L);

        assertThat(outcome.attempted()).isZero();
        assertThat(outcome.refunded()).isZero();
        verify(paymentService, never()).cancelPaymentAuthorization(anyString());
        verify(adRepository, never()).save(any());
    }
}
