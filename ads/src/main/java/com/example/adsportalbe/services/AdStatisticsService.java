package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.AdImpressionDto;
import com.example.adsportalbe.dto.CachedAd;
import com.example.adsportalbe.dto.ServedAdDto;
import com.example.adsportalbe.dto.ad.AdDailyStatsResponseDto;
import com.example.adsportalbe.dto.ad.UserAdViewsDailyBreakdownDto;
import com.example.adsportalbe.dto.ad.UserAdViewsSummaryDto;
import com.example.adsportalbe.enums.AdStatus;
import com.example.adsportalbe.models.ad.Ad;
import com.example.adsportalbe.models.ad.AdDailyStatistics;
import com.example.adsportalbe.models.ad.AdImpression;
import com.mk3.chatapp.models.identity.User;
import com.example.adsportalbe.repositories.AdDailyStatisticsRepository;
import com.example.adsportalbe.repositories.AdImpressionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdStatisticsService {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private final AdService adService;
    private final AdImpressionRepository adImpressionRepository;
    private final AdDailyStatisticsRepository adDailyStatisticsRepository;
    private final AdCacheService adCacheService;
    private final AdImpressionCacheService adImpressionCacheService;

    @Transactional
    public void processImpressions(List<AdImpressionDto> impressionDtos) {
        if (impressionDtos == null || impressionDtos.isEmpty()) {
            log.debug("No impressions to process");
            return;
        }

        log.info("Processing {} ad impressions", impressionDtos.size());

        Map<Long, List<AdImpressionDto>> impressionsByAdId = groupImpressionsByAdId(impressionDtos);
        Set<Long> adIds = impressionsByAdId.keySet();
        Map<Long, Ad> adsById = fetchAdsAsMap(adIds);
        Map<DailyStatsKey, AdDailyStatistics> existingDailyStats = fetchExistingDailyStats(impressionsByAdId, adsById);

        List<AdImpression> impressionsToSave = new ArrayList<>();
        List<AdDailyStatistics> dailyStatsToSave = new ArrayList<>();
        List<Ad> adsToUpdate = new ArrayList<>();
        Set<Long> completedAdIds = new HashSet<>();

        impressionsByAdId.forEach((adId, impressions) -> {
            Ad ad = adsById.get(adId);
            if (ad == null) {
                throw new RuntimeException("Ad not found with ID: " + adId);
            }

            List<AdImpression> newImpressions = buildImpressions(ad, impressions);
            impressionsToSave.addAll(newImpressions);

            Map<LocalDate, Long> dailyCounts = aggregateDailyCounts(impressions);

            dailyCounts.forEach((date, count) -> {
                AdDailyStatistics stats = getOrCreateDailyStats(ad, date, existingDailyStats);
                stats.setViewsCount(stats.getViewsCount() + count);
                dailyStatsToSave.add(stats);
            });

            boolean completed = updateAdViewsAndStatus(ad, impressions.size());
            if (completed && ad.getId() != null) {
                completedAdIds.add(ad.getId());
            }
            adsToUpdate.add(ad);
        });

        batchSave(impressionsToSave, dailyStatsToSave, adsToUpdate);
        removeCompletedAdsFromCache(completedAdIds);

        log.info("Successfully processed {} impressions across {} ads",
                impressionDtos.size(), adsById.size());
    }

    /**
     * Processes impressions for a single ad.
     *
     * @param adId           the ID of the ad
     * @param impressionDtos list of impressions for this ad
     */
    @Transactional
    public void processImpressionsForAd(Long adId, List<AdImpressionDto> impressionDtos) {
        if (adId == null || impressionDtos == null || impressionDtos.isEmpty()) {
            log.debug("No impressions to process for ad ID: {}", adId);
            return;
        }

        log.info("Processing {} impressions for ad ID: {}", impressionDtos.size(), adId);

        // Fetch the ad
        List<Ad> ads = adService.findAllById(List.of(adId));
        if (ads.isEmpty()) {
            log.error("Ad not found with ID: {}", adId);
            adCacheService.removeAd(adId);
            return;
        }
        Ad ad = ads.get(0);

        // Fetch existing daily stats for this ad
        Set<LocalDate> dates = impressionDtos.stream()
                .map(dto -> toLocalDate(dto.getTimestamp()))
                .collect(Collectors.toSet());

        Map<DailyStatsKey, AdDailyStatistics> existingDailyStats = adDailyStatisticsRepository
                .findByAdIdInAndDateIn(Set.of(adId), dates).stream()
                .collect(Collectors.toMap(
                        stats -> new DailyStatsKey(stats.getAd().getId(), stats.getDate()),
                        stats -> stats));

        // Build impressions
        List<AdImpression> impressionsToSave = buildImpressions(ad, impressionDtos);

        // Aggregate daily counts and update stats
        List<AdDailyStatistics> dailyStatsToSave = new ArrayList<>();
        Map<LocalDate, Long> dailyCounts = aggregateDailyCounts(impressionDtos);

        dailyCounts.forEach((date, count) -> {
            AdDailyStatistics stats = getOrCreateDailyStats(ad, date, existingDailyStats);
            stats.setViewsCount(stats.getViewsCount() + count);
            dailyStatsToSave.add(stats);
        });

        // Update ad views and status
        boolean completed = updateAdViewsAndStatus(ad, impressionDtos.size());

        // Save everything
        batchSave(impressionsToSave, dailyStatsToSave, List.of(ad));

        if (completed) {
            adCacheService.removeAd(adId);
        }

        log.info("Successfully processed {} impressions for ad ID: {}", impressionDtos.size(), adId);
    }

    private Map<Long, List<AdImpressionDto>> groupImpressionsByAdId(List<AdImpressionDto> impressions) {
        return impressions.stream()
                .collect(Collectors.groupingBy(AdImpressionDto::getAdId));
    }

    private Map<Long, Ad> fetchAdsAsMap(Set<Long> adIds) {
        return adService.findAllById(adIds).stream()
                .collect(Collectors.toMap(Ad::getId, ad -> ad));
    }

    private Map<DailyStatsKey, AdDailyStatistics> fetchExistingDailyStats(
            Map<Long, List<AdImpressionDto>> impressionsByAdId,
            Map<Long, Ad> adsById) {

        Set<DailyStatsKey> keysToFetch = impressionsByAdId.entrySet().stream()
                .filter(entry -> adsById.containsKey(entry.getKey()))
                .flatMap(entry -> {
                    Long adId = entry.getKey();
                    return entry.getValue().stream()
                            .map(dto -> toLocalDate(dto.getTimestamp()))
                            .distinct()
                            .map(date -> new DailyStatsKey(adId, date));
                })
                .collect(Collectors.toSet());

        if (keysToFetch.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> adIds = keysToFetch.stream()
                .map(DailyStatsKey::adId)
                .collect(Collectors.toSet());

        Set<LocalDate> dates = keysToFetch.stream()
                .map(DailyStatsKey::date)
                .collect(Collectors.toSet());

        return adDailyStatisticsRepository.findByAdIdInAndDateIn(adIds, dates).stream()
                .collect(Collectors.toMap(
                        stats -> new DailyStatsKey(stats.getAd().getId(), stats.getDate()),
                        stats -> stats));
    }

    private List<AdImpression> buildImpressions(Ad ad, List<AdImpressionDto> dtos) {
        return dtos.stream()
                .map(dto -> AdImpression.builder()
                        .ad(ad)
                        .timestamp(dto.getTimestamp())
                        .ipAddress(dto.getIpAddress())
                        .userId(dto.getUserId())
                        .build())
                .collect(Collectors.toList());
    }

    private Map<LocalDate, Long> aggregateDailyCounts(List<AdImpressionDto> impressions) {
        return impressions.stream()
                .collect(Collectors.groupingBy(
                        dto -> toLocalDate(dto.getTimestamp()),
                        Collectors.counting()));
    }

    private LocalDate toLocalDate(Instant timestamp) {
        return timestamp.atZone(UTC).toLocalDate();
    }

    private AdDailyStatistics getOrCreateDailyStats(
            Ad ad,
            LocalDate date,
            Map<DailyStatsKey, AdDailyStatistics> existingStats) {

        return existingStats.computeIfAbsent(
                new DailyStatsKey(ad.getId(), date),
                key -> AdDailyStatistics.builder()
                        .ad(ad)
                        .date(date)
                        .viewsCount(0L)
                        .build());
    }

    private boolean updateAdViewsAndStatus(Ad ad, int newViewsCount) {
        int currentViews = Optional.ofNullable(ad.getServedViews()).orElse(0);
        ad.setServedViews(currentViews + newViewsCount);

        if (shouldMarkAsCompleted(ad)) {
            ad.setStatus(AdStatus.COMPLETED);
            log.info("Ad {} reached completion threshold ({}/{}). Status updated to COMPLETED.",
                    ad.getId(), ad.getServedViews(), ad.getTotalViewsBought());
            return true;
        }
        return false;
    }

    private boolean shouldMarkAsCompleted(Ad ad) {
        return ad.getStatus() == AdStatus.ACTIVE
                && ad.getTotalViewsBought() != null
                && ad.getServedViews() >= ad.getTotalViewsBought();
    }

    private void batchSave(
            List<AdImpression> impressions,
            List<AdDailyStatistics> dailyStats,
            List<Ad> ads) {

        adImpressionRepository.saveAll(impressions);
        adDailyStatisticsRepository.saveAll(dailyStats);
        adService.saveAll(ads);
    }

    private void removeCompletedAdsFromCache(Set<Long> completedAdIds) {
        if (completedAdIds == null || completedAdIds.isEmpty()) {
            return;
        }
        for (Long completedAdId : completedAdIds) {
            try {
                adCacheService.removeAd(completedAdId);
            } catch (Exception e) {
                log.error("Failed to remove completed ad {} from cache", completedAdId, e);
            }
        }
    }

    @Transactional
    public ServedAdDto serveAd(Long userId, String ipAddress) {
        CachedAd cachedAd = adCacheService.chooseRandomAd();
        if (cachedAd == null) {
            log.info("No ads available to serve");
            return null;
        }

        // Increment views in cache
        adCacheService.incrementViewCount(cachedAd.getId());

        // Create and cache impression
        AdImpressionDto impressionDto = AdImpressionDto.builder()
                .adId(cachedAd.getId())
                .userId(userId)
                .ipAddress(ipAddress)
                .timestamp(Instant.now())
                .build();
        adImpressionCacheService.cacheImpression(impressionDto);

        long totalServed = (cachedAd.getServedViews() != null ? cachedAd.getServedViews() : 0) + 1;

        if (cachedAd.getTotalViewsBought() != null && totalServed >= cachedAd.getTotalViewsBought()) {
            completeAd(cachedAd.getId());
        }

        return ServedAdDto.builder()
                .id(cachedAd.getId())
                .title(cachedAd.getTitle())
                .textContent(cachedAd.getTextContent())
                .imageUrl(cachedAd.getImageUrl())
                .videoUrl(cachedAd.getVideoUrl())
                .format(cachedAd.getFormat() != null ? cachedAd.getFormat().name() : null)
                .build();
    }

    private void completeAd(Long adId) {
        // Remove from cache to stop serving
        adCacheService.removeAd(adId);

        // Pop and process impressions immediately
        List<AdImpressionDto> impressions = adImpressionCacheService.popImpressionsByAdId(adId);
        if (!impressions.isEmpty()) {
            processImpressionsForAd(adId, impressions);
            log.info("Ad {} reached completion threshold. Removed from cache and processed {} impressions immediately.",
                    adId, impressions.size());
        } else {
            log.info("Ad {} reached completion threshold. Removed from cache. No cached impressions to process.", adId);
        }
    }

    /**
     * Retrieves daily statistics for an ad.
     *
     * @param adId     the ID of the ad
     * @param fromDate optional date from which to retrieve stats (inclusive)
     * @param user     the user requesting the stats
     * @return AdDailyStatsResponseDto containing daily stats, today's views,
     * yesterday's views, viewsBought, and servedViews
     */
    public AdDailyStatsResponseDto getAdDailyStats(Long adId, LocalDate fromDate, User user) {
        // Fetch the ad
        List<Ad> ads = adService.findAllById(List.of(adId));
        if (ads.isEmpty()) {
            throw new RuntimeException("Ad not found with ID: " + adId);
        }
        Ad ad = ads.get(0);

        // Check ownership
        if (user.getRole() != com.mk3.chatapp.enums.Role.ADMIN && !ad.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have permission to view stats for this ad.");
        }

        // Fetch daily statistics based on whether fromDate is provided
        List<AdDailyStatistics> dailyStatistics;
        if (fromDate != null) {
            dailyStatistics = adDailyStatisticsRepository.findByAdIdAndDateFrom(adId, fromDate);
        } else {
            dailyStatistics = adDailyStatisticsRepository.findByAdIdOrderByDateDesc(adId);
        }

        // Calculate today's views
        LocalDate today = LocalDate.now(UTC);
        Long todaysViews = dailyStatistics.stream()
                .filter(stat -> stat.getDate().equals(today))
                .map(AdDailyStatistics::getViewsCount)
                .findFirst()
                .orElse(0L);

        // Calculate yesterday's views
        LocalDate yesterday = today.minusDays(1);
        Long yesterdaysViews = dailyStatistics.stream()
                .filter(stat -> stat.getDate().equals(yesterday))
                .map(AdDailyStatistics::getViewsCount)
                .findFirst()
                .orElse(0L);

        // Map to DTOs
        List<AdDailyStatsResponseDto.DailyStatDto> dailyStatDtos = dailyStatistics.stream()
                .map(stat -> AdDailyStatsResponseDto.DailyStatDto.builder()
                        .date(stat.getDate())
                        .viewsCount(stat.getViewsCount())
                        .build())
                .toList();

        return AdDailyStatsResponseDto.builder()
                .adId(adId)
                .viewsBought(ad.getTotalViewsBought())
                .servedViews(ad.getServedViews())
                .todaysViews(todaysViews)
                .yesterdaysViews(yesterdaysViews)
                .dailyStats(dailyStatDtos)
                .build();
    }

    /**
     * Gets a summary of the user's ad views including today's views, yesterday's
     * views,
     * total views bought across all ads, and total served views across all ads.
     *
     * @param user the user to get stats for
     * @return UserAdViewsSummaryDto containing aggregated stats
     */
    public UserAdViewsSummaryDto getUserAdViewsSummary(User user) {
        LocalDate today = LocalDate.now(UTC);
        LocalDate yesterday = today.minusDays(1);

        // Fetch daily stats for today and yesterday for the user's ads
        List<AdDailyStatistics> dailyStats = adDailyStatisticsRepository
                .findByOwnerIdAndDateIn(user.getId(), Set.of(today, yesterday));

        Long todaysViews = dailyStats.stream()
                .filter(stat -> stat.getDate().equals(today))
                .mapToLong(AdDailyStatistics::getViewsCount)
                .sum();

        Long yesterdaysViews = dailyStats.stream()
                .filter(stat -> stat.getDate().equals(yesterday))
                .mapToLong(AdDailyStatistics::getViewsCount)
                .sum();

        // Fetch all user's ads to sum up total views bought and served views
        List<Ad> userAds = adService.findAllByOwnerId(user.getId());

        Integer totalViewsBought = userAds.stream()
                .map(Ad::getTotalViewsBought)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        Integer totalServedViews = userAds.stream()
                .map(Ad::getServedViews)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return UserAdViewsSummaryDto.builder()
                .todaysViews(todaysViews)
                .yesterdaysViews(yesterdaysViews)
                .totalViewsBought(totalViewsBought)
                .totalServedViews(totalServedViews)
                .build();
    }

    /**
     * Gets a day-by-day breakdown of total views for all of the user's ads.
     *
     * @param user     the user to get stats for
     * @param fromDate optional date to filter from (inclusive)
     * @return UserAdViewsDailyBreakdownDto containing daily breakdown
     */
    public UserAdViewsDailyBreakdownDto getUserAdViewsDailyBreakdown(User user, LocalDate fromDate) {
        List<AdDailyStatistics> dailyStats;

        if (fromDate != null) {
            dailyStats = adDailyStatisticsRepository.findByOwnerIdAndDateFrom(user.getId(), fromDate);
        } else {
            dailyStats = adDailyStatisticsRepository.findByOwnerIdOrderByDateDesc(user.getId());
        }

        // Aggregate views by date across all ads
        Map<LocalDate, Long> viewsByDate = dailyStats.stream()
                .collect(Collectors.groupingBy(
                        AdDailyStatistics::getDate,
                        Collectors.summingLong(AdDailyStatistics::getViewsCount)));

        // Convert to list of DailyViewStats, sorted by date descending
        List<UserAdViewsDailyBreakdownDto.DailyViewStats> dailyViewStats = viewsByDate.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Long>comparingByKey().reversed())
                .map(entry -> UserAdViewsDailyBreakdownDto.DailyViewStats.builder()
                        .date(entry.getKey())
                        .viewsCount(entry.getValue())
                        .build())
                .toList();

        return UserAdViewsDailyBreakdownDto.builder()
                .dailyViews(dailyViewStats)
                .build();
    }

    private record DailyStatsKey(Long adId, LocalDate date) {
    }
}
