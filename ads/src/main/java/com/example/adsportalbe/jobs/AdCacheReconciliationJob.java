package com.example.adsportalbe.jobs;

import com.example.adsportalbe.enums.AdStatus;
import com.example.adsportalbe.mappers.AdMapper;
import com.example.adsportalbe.models.ad.Ad;
import com.example.adsportalbe.repositories.AdRepository;
import com.example.adsportalbe.services.AdCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdCacheReconciliationJob {

    private final AdRepository adRepository;
    private final AdCacheService adCacheService;
    private final AdMapper adMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCacheOnStartup() {
        reconcile("startup");
    }

    @Scheduled(
            fixedDelayString = "${ads.cache.reconcile.fixed-delay-ms:60000}",
            initialDelayString = "${ads.cache.reconcile.initial-delay-ms:30000}")
    public void reconcileActiveAdsWithCache() {
        reconcile("scheduled");
    }

    private void reconcile(String trigger) {
        try {
            List<Ad> dbActiveAds = adRepository.findAllByStatus(AdStatus.ACTIVE);
            Set<Long> dbActiveAdIds = dbActiveAds.stream()
                    .map(Ad::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());

            Set<Long> cachedActiveAdIds = adCacheService.getAllActiveAdIds();

            int added = 0;
            int removed = 0;

            for (Ad ad : dbActiveAds) {
                if (ad.getId() == null || cachedActiveAdIds.contains(ad.getId())) {
                    continue;
                }
                adCacheService.cacheAd(adMapper.toCachedAd(ad));
                added++;
            }

            for (Long cachedAdId : cachedActiveAdIds) {
                if (dbActiveAdIds.contains(cachedAdId)) {
                    continue;
                }
                adCacheService.removeAd(cachedAdId);
                removed++;
            }

            if (added > 0 || removed > 0) {
                log.info("Ad cache reconciliation ({}): added={}, removed={}, activeInDb={}, activeInCacheBefore={}",
                        trigger, added, removed, dbActiveAdIds.size(), cachedActiveAdIds.size());
            } else {
                log.debug("Ad cache reconciliation ({}) completed with no changes. activeInDb={}, activeInCache={}",
                        trigger, dbActiveAdIds.size(), cachedActiveAdIds.size());
            }
        } catch (Exception e) {
            log.error("Ad cache reconciliation ({}) failed", trigger, e);
        }
    }
}
