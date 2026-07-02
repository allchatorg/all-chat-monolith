package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.ad.*;
import com.example.adsportalbe.dto.requests.AdSearchRequestDto;
import com.example.adsportalbe.models.ad.Ad;
import com.mk3.chatapp.models.identity.User;
import com.stripe.exception.StripeException;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface AdService {
    Ad createAd(CreateAdRequestDto request, User user) throws StripeException;

    Page<AdDto> searchAds(AdSearchRequestDto request);

    List<AdStatusCountDto> getAdStatusCounts();

    List<AdStatusCountDto> getAdStatusCountsByUserId(Long userId);

    BanAdsSummaryDto getBanAdsSummary(Long userId);

    /**
     * Cancels the payment authorization for every pending (SUBMITTED +
     * AUTHORIZED) ad of the user, marking each ad REJECTED and its receipt
     * CANCELLED. Per-ad failures are logged and skipped, never thrown.
     */
    PendingAdRefundOutcome refundPendingAdsForUser(Long userId);

    record PendingAdRefundOutcome(int attempted, int refunded, double totalRefunded, String currency) {}

    AdDetailedViewDto getAdById(Long id, User user);

    AdDetailedViewDto rejectAd(Long adId, String rejectionReason) throws StripeException;

    AdDetailedViewDto approveAd(Long adId) throws StripeException;

    RevenueDto getDailyRevenueStats();

    MonthlyRevenueResponseDto getMonthlyRevenueStats();

    WeeklyRevenueResponseDto getWeeklyRevenueStats();

    Ad save(Ad ad);

    List<Ad> saveAll(List<Ad> ads);

    List<Ad> findAllById(Iterable<Long> ids);

    List<Ad> findAllByOwnerId(Long ownerId);

    PurchasedAdsDailyCountDto getPurchasedAdsDailyCounts(LocalDate fromDate);
}
