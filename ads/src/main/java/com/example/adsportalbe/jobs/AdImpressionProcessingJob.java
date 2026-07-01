package com.example.adsportalbe.jobs;

import com.example.adsportalbe.services.AdCacheService;
import com.example.adsportalbe.services.AdImpressionCacheService;
import com.example.adsportalbe.services.AdStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdImpressionProcessingJob {

    private final AdCacheService adCacheService;
    private final AdImpressionCacheService adImpressionCacheService;
    private final AdStatisticsService adStatisticsService;

    @Scheduled(fixedRate = 5000)
    public void processImpressionsFromCache() {
//        log.trace("Starting scheduled ad impression processing");

        Set<Long> activeAdIds = adCacheService.getAllActiveAdIds();

        if (activeAdIds.isEmpty()) {
            log.trace("No active ads in cache");
            return;
        }

//        log.debug("Processing impressions for {} active ads", activeAdIds.size());

        for (Long adId : activeAdIds) {
            try {
                AdImpressionCacheService.PoppedImpressions popped =
                        adImpressionCacheService.popImpressionsByAdId(adId);

                if (popped.processingKey() == null) {
                    // Nothing was claimed for this ad.
                    continue;
                }

                if (!popped.isEmpty()) {
//                    log.info("Processing {} impressions for ad ID: {}", popped.impressions().size(), adId);
                    adStatisticsService.processImpressionsForAd(adId, popped.impressions());
                }

                // Only drop the buffered impressions once they are durably persisted.
                adImpressionCacheService.deleteProcessingKey(popped.processingKey());
            } catch (Exception e) {
                log.error("Failed to process impressions for ad ID: {}", adId, e);
                // Leave the processing key in place so impressions are retried, not lost.
            }
        }
    }
}
