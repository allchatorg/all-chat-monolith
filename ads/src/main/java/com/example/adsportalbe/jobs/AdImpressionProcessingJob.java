package com.example.adsportalbe.jobs;

import com.example.adsportalbe.dto.AdImpressionDto;
import com.example.adsportalbe.services.AdCacheService;
import com.example.adsportalbe.services.AdImpressionCacheService;
import com.example.adsportalbe.services.AdStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
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
                List<AdImpressionDto> impressions = adImpressionCacheService.popImpressionsByAdId(adId);

                if (impressions.isEmpty()) {
                    continue;
                }

//                log.info("Processing {} impressions for ad ID: {}", impressions.size(), adId);
                adStatisticsService.processImpressionsForAd(adId, impressions);
            } catch (Exception e) {
                log.error("Failed to process impressions for ad ID: {}", adId, e);
                // Continue processing other ads even if one fails
            }
        }
    }
}
