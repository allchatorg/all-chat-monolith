package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.AdImpressionDto;
import com.example.adsportalbe.dto.CachedAd;
import com.example.adsportalbe.dto.ServedAdDto;
import com.example.adsportalbe.dto.ad.AdDailyStatsResponseDto;
import com.example.adsportalbe.dto.ad.UserAdViewsDailyBreakdownDto;
import com.example.adsportalbe.dto.ad.UserAdViewsSummaryDto;
import com.example.adsportalbe.enums.AdStatus;
import com.example.adsportalbe.enums.PurchaseType;
import com.example.adsportalbe.models.ad.Ad;
import com.example.adsportalbe.models.ad.AdClick;
import com.example.adsportalbe.models.ad.AdDailyStatistics;
import com.example.adsportalbe.models.ad.AdFormatType;
import com.example.adsportalbe.models.ad.AdHyperlinkClick;
import com.example.adsportalbe.models.ad.AdImpression;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.FileUploadService;
import com.mk3.chatapp.utils.MessageMarkers;
import com.example.adsportalbe.repositories.AdClickRepository;
import com.example.adsportalbe.repositories.AdDailyStatisticsRepository;
import com.example.adsportalbe.repositories.AdHyperlinkClickRepository;
import com.example.adsportalbe.repositories.AdImpressionRepository;
import com.example.adsportalbe.repositories.AdRepository;
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

    private static final int MAX_LINK_URL_LENGTH = 1024;

    private final AdService adService;
    private final AdRepository adRepository;
    private final AdImpressionRepository adImpressionRepository;
    private final AdClickRepository adClickRepository;
    private final AdDailyStatisticsRepository adDailyStatisticsRepository;
    private final AdHyperlinkClickRepository adHyperlinkClickRepository;
    private final AdCacheService adCacheService;
    private final AdImpressionCacheService adImpressionCacheService;
    private final FileUploadService fileUploadService;
    private final PurchaseCommunicationService purchaseCommunicationService;

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
        List<Ad> ads = adRepository.findAllByIdForUpdate(List.of(adId));
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
        return adRepository.findAllByIdForUpdate(adIds).stream()
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
            purchaseCommunicationService.notifyOwner(ad.getOwner(), PurchaseType.AD, ad.getId(), NotificationType.AD_COMPLETED,
                    "Your ad campaign is complete",
                    "Your ad \"" + ad.getTitle() + "\" has served all " + ad.getTotalViewsBought()
                            + " purchased views.",
                    ad.getReceipt());
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
                // Cached values are storage keys; resolve at serve time because
                // dev presigned URLs expire while cache entries live long.
                .imageUrl(fileUploadService.getFileUrl(cachedAd.getImageUrl()))
                .videoUrl(fileUploadService.getFileUrl(cachedAd.getVideoUrl()))
                .format(cachedAd.getFormat() != null ? cachedAd.getFormat().name() : null)
                .build();
    }

    /**
     * Records a click-through on a photo/video ad (the user opened the ad's media
     * overlay in chat). Deduplicated per user per ad per UTC day; repeat clicks
     * the same day are silent no-ops. Clicks are written directly (no cache
     * buffer) — they are far rarer than impressions and should show up in the
     * advertiser dashboard immediately.
     */
    @Transactional
    public void registerClick(Long adId, Long userId, String ipAddress) {
        List<Ad> ads = adService.findAllById(List.of(adId));
        if (ads.isEmpty()) {
            log.warn("Click ignored: ad not found with ID: {}", adId);
            return;
        }
        Ad ad = ads.get(0);

        if (ad.getFormat() != null && ad.getFormat().getType() == AdFormatType.TEXT) {
            log.debug("Click ignored: ad {} is a TEXT ad", adId);
            return;
        }

        LocalDate today = LocalDate.now(UTC);
        if (adClickRepository.existsByAd_IdAndUserIdAndClickDate(adId, userId, today)) {
            log.debug("Click ignored: user {} already clicked ad {} today", userId, adId);
            return;
        }

        adClickRepository.save(AdClick.builder()
                .ad(ad)
                .timestamp(Instant.now())
                .clickDate(today)
                .ipAddress(ipAddress)
                .userId(userId)
                .build());

        AdDailyStatistics stats = adDailyStatisticsRepository.findByAdIdAndDate(adId, today)
                .orElseGet(() -> AdDailyStatistics.builder()
                        .ad(ad)
                        .date(today)
                        .viewsCount(0L)
                        .build());
        stats.setClicksCount(Optional.ofNullable(stats.getClicksCount()).orElse(0L) + 1);
        adDailyStatisticsRepository.save(stats);

        log.debug("Registered click for ad {} by user {}", adId, userId);
    }

    /**
     * Records a click on a specific hyperlink inside an ad's text content. Applies
     * to all ad formats, including TEXT. Fully separate from {@link #registerClick}
     * media click-throughs — does not touch AdDailyStatistics or CTR. The URL must
     * appear verbatim in the ad's textContent; anything else is dropped. Deduplicated
     * per user per link per UTC day; repeat clicks the same day are silent no-ops.
     */
    @Transactional
    public void registerLinkClick(Long adId, String url, Long userId, String ipAddress) {
        if (url == null || url.length() > MAX_LINK_URL_LENGTH) {
            log.warn("Link click ignored: missing or oversized url for ad {}", adId);
            return;
        }

        List<Ad> ads = adService.findAllById(List.of(adId));
        if (ads.isEmpty()) {
            log.warn("Link click ignored: ad not found with ID: {}", adId);
            return;
        }
        Ad ad = ads.get(0);

        if (!extractHyperlinks(ad.getTextContent()).contains(url)) {
            log.warn("Link click ignored: url not present in text of ad {}", adId);
            return;
        }

        LocalDate today = LocalDate.now(UTC);
        if (adHyperlinkClickRepository.existsByAd_IdAndLinkUrlAndUserIdAndClickDate(adId, url, userId, today)) {
            log.debug("Link click ignored: user {} already clicked this link of ad {} today", userId, adId);
            return;
        }

        adHyperlinkClickRepository.save(AdHyperlinkClick.builder()
                .ad(ad)
                .linkUrl(url)
                .timestamp(Instant.now())
                .clickDate(today)
                .ipAddress(ipAddress)
                .userId(userId)
                .build());

        log.debug("Registered link click for ad {} by user {}", adId, userId);
    }

    // Distinct hyperlinks in text order, as the chat client sees them.
    // Delegates to MessageMarkers so URL detection (including trimming of
    // trailing formatting asterisks) stays identical to the frontend's
    // tokenize()/extractFormattedUrls in messageMarkers.ts.
    private static List<String> extractHyperlinks(String textContent) {
        return MessageMarkers.extractUrls(textContent).stream()
                .distinct()
                .toList();
    }

    private void completeAd(Long adId) {
        // Remove from cache to stop serving
        adCacheService.removeAd(adId);

        // Pop and process impressions immediately
        AdImpressionCacheService.PoppedImpressions popped = adImpressionCacheService.popImpressionsByAdId(adId);
        if (popped.processingKey() == null) {
            log.info("Ad {} reached completion threshold. Removed from cache. No cached impressions to process.", adId);
            return;
        }

        if (!popped.isEmpty()) {
            processImpressionsForAd(adId, popped.impressions());
            log.info("Ad {} reached completion threshold. Removed from cache and processed {} impressions immediately.",
                    adId, popped.impressions().size());
        } else {
            log.info("Ad {} reached completion threshold. Removed from cache. No cached impressions to process.", adId);
        }

        // Only drop the buffered impressions once they are durably persisted.
        adImpressionCacheService.deleteProcessingKey(popped.processingKey());
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

        // Access control: regular users may only view stats for their own ads;
        // staff (MODERATOR/ADMIN/SUPER_ADMIN) may view any ad's stats. This mirrors
        // AdServiceImpl#getAdById so the same accounts that can open an ad can also
        // see its stats. Role is a hierarchy (SUPER_ADMIN > ADMIN > MODERATOR), so an
        // exact `!= ADMIN` check wrongly excluded SUPER_ADMIN and broke the admin view.
        if (user.getRole() == com.mk3.chatapp.enums.Role.USER && !ad.getOwner().getId().equals(user.getId())) {
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

        Long todaysClicks = dailyStatistics.stream()
                .filter(stat -> stat.getDate().equals(today))
                .map(stat -> Optional.ofNullable(stat.getClicksCount()).orElse(0L))
                .findFirst()
                .orElse(0L);

        // Lifetime click count, independent of fromDate (like servedViews)
        long totalClicks = adClickRepository.countByAd_Id(adId);

        // Map to DTOs
        List<AdDailyStatsResponseDto.DailyStatDto> dailyStatDtos = dailyStatistics.stream()
                .map(stat -> {
                    long clicks = Optional.ofNullable(stat.getClicksCount()).orElse(0L);
                    return AdDailyStatsResponseDto.DailyStatDto.builder()
                            .date(stat.getDate())
                            .viewsCount(stat.getViewsCount())
                            .clicksCount(clicks)
                            .ctr(computeCtr(clicks, stat.getViewsCount()))
                            .build();
                })
                .toList();

        return AdDailyStatsResponseDto.builder()
                .adId(adId)
                .viewsBought(ad.getTotalViewsBought())
                .servedViews(ad.getServedViews())
                .todaysViews(todaysViews)
                .yesterdaysViews(yesterdaysViews)
                .totalClicks(totalClicks)
                .todaysClicks(todaysClicks)
                .overallCtr(computeCtr(totalClicks, ad.getServedViews() != null ? ad.getServedViews().longValue() : 0L))
                .dailyStats(dailyStatDtos)
                .linkStats(buildLinkStats(ad, fromDate, today))
                .build();
    }

    // Links are derived from textContent (not from recorded clicks) so links
    // with zero clicks still appear in the advertiser's per-link breakdown.
    private List<AdDailyStatsResponseDto.LinkStatDto> buildLinkStats(Ad ad, LocalDate fromDate, LocalDate today) {
        List<String> links = extractHyperlinks(ad.getTextContent());
        if (links.isEmpty()) {
            return List.of();
        }

        Map<String, List<AdHyperlinkClickRepository.LinkDailyCount>> countsByUrl =
                adHyperlinkClickRepository.countDailyByAdId(ad.getId()).stream()
                        .collect(Collectors.groupingBy(AdHyperlinkClickRepository.LinkDailyCount::getLinkUrl));

        return links.stream()
                .map(url -> {
                    List<AdHyperlinkClickRepository.LinkDailyCount> rows =
                            countsByUrl.getOrDefault(url, List.of());

                    // Lifetime total, independent of fromDate (like totalClicks)
                    long linkTotalClicks = rows.stream()
                            .mapToLong(AdHyperlinkClickRepository.LinkDailyCount::getClicks)
                            .sum();

                    long linkTodaysClicks = rows.stream()
                            .filter(row -> row.getClickDate().equals(today))
                            .mapToLong(AdHyperlinkClickRepository.LinkDailyCount::getClicks)
                            .findFirst()
                            .orElse(0L);

                    // Same date-desc order and fromDate filter as dailyStats
                    List<AdDailyStatsResponseDto.LinkDailyStatDto> linkDailyStats = rows.stream()
                            .filter(row -> fromDate == null || !row.getClickDate().isBefore(fromDate))
                            .map(row -> AdDailyStatsResponseDto.LinkDailyStatDto.builder()
                                    .date(row.getClickDate())
                                    .clicksCount(row.getClicks())
                                    .build())
                            .toList();

                    return AdDailyStatsResponseDto.LinkStatDto.builder()
                            .url(url)
                            .totalClicks(linkTotalClicks)
                            .todaysClicks(linkTodaysClicks)
                            .dailyStats(linkDailyStats)
                            .build();
                })
                .toList();
    }

    private Double computeCtr(long clicks, Long views) {
        return views != null && views > 0 ? clicks / (double) views : 0.0;
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

        long totalClicks = adClickRepository.countByOwnerId(user.getId());

        return UserAdViewsSummaryDto.builder()
                .todaysViews(todaysViews)
                .yesterdaysViews(yesterdaysViews)
                .totalViewsBought(totalViewsBought)
                .totalServedViews(totalServedViews)
                .totalClicks(totalClicks)
                .overallCtr(computeCtr(totalClicks, totalServedViews.longValue()))
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
