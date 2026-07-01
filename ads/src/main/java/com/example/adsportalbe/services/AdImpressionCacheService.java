package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.AdImpressionDto;
import com.example.adsportalbe.models.ad.Ad;
import com.example.adsportalbe.models.ad.AdImpression;
import com.example.adsportalbe.repositories.AdImpressionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdImpressionCacheService {

    private static final String GLOBAL_IMPRESSION_KEY = "ad:impression:global";
    private static final String AD_IMPRESSION_KEY_PREFIX = "ad:impression:";

    @Qualifier("adsRedisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;
    private final AdImpressionRepository adImpressionRepository;
    private final AdService adService;

    /**
     * Caches a single ad impression in Redis.
     *
     * @param impressionDto the impression DTO to cache
     */
    public void cacheImpression(AdImpressionDto impressionDto) {
        if (impressionDto == null || impressionDto.getAdId() == null) {
            log.warn("Attempted to cache null impression or impression without ad ID");
            return;
        }

        String adImpressionKey = AD_IMPRESSION_KEY_PREFIX + impressionDto.getAdId();
        redisTemplate.opsForList().rightPush(adImpressionKey, impressionDto);
        log.debug("Cached impression for ad ID: {}", impressionDto.getAdId());
    }

    /**
     * Pops all impressions from the global queue atomically.
     *
     * @return list of all pending ad impression DTOs
     */
    public List<AdImpressionDto> popAllImpressions() {
        // We simply retrieve everything and delete the key.
        // This is safe because LPOP/DEL combination is not strictly atomic unless in a
        // transaction,
        // but since we want "ALL current items", we can use MULTI/EXEC or just accept
        // that
        // between RANGE and DEL more items might come in.
        // A better approach for "drain queue" pattern without lua:
        // Use RENAME to move to a temp key, then read from temp key.
        // Or simpler for this scope: Just read all and delete.
        // CAUTION: If we read, then new item comes, then delete -> we lose the new
        // item.
        // To be safe, we should use getAndSet logic or RENAME.
        // Let's use RENAME to a processing key pattern to be robust.

        String processingKey = GLOBAL_IMPRESSION_KEY + ":processing:" + System.currentTimeMillis();

        try {
            // Rename the key. If key doesn't exist (no impressions), this throws an error
            // or returns false.
            if (Boolean.FALSE.equals(redisTemplate.hasKey(GLOBAL_IMPRESSION_KEY))) {
                return List.of();
            }
            redisTemplate.rename(GLOBAL_IMPRESSION_KEY, processingKey);
        } catch (Exception e) {
            // Likely key didn't exist or race condition
            return List.of();
        }

        List<Object> objects = redisTemplate.opsForList().range(processingKey, 0, -1);
        redisTemplate.delete(processingKey);

        return toImpressionDtos(objects, processingKey);
    }

    /**
     * Pops all impressions for a specific ad atomically into a private processing
     * key. The processing key is NOT deleted here: the caller must call
     * {@link #deleteProcessingKey(String)} only after the impressions have been
     * durably persisted, so a failure during processing does not silently lose
     * the buffered impressions.
     *
     * @param adId the ID of the ad
     * @return holder with the processing key and the parsed impression DTOs; an
     * {@link PoppedImpressions#empty()} holder (null key) when there was nothing
     * to pop
     */
    public PoppedImpressions popImpressionsByAdId(Long adId) {
        if (adId == null) {
            return PoppedImpressions.empty();
        }

        String adImpressionKey = AD_IMPRESSION_KEY_PREFIX + adId;
        String processingKey = adImpressionKey + ":processing:" + System.currentTimeMillis();

        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(adImpressionKey))) {
                return PoppedImpressions.empty();
            }
            redisTemplate.rename(adImpressionKey, processingKey);
        } catch (Exception e) {
            log.debug("No impressions to pop for ad ID: {}", adId);
            return PoppedImpressions.empty();
        }

        List<Object> objects = redisTemplate.opsForList().range(processingKey, 0, -1);
        return new PoppedImpressions(processingKey, toImpressionDtos(objects, processingKey));
    }

    /**
     * Deletes a processing key produced by {@link #popImpressionsByAdId(Long)}.
     * Call this only after the popped impressions have been persisted.
     *
     * @param processingKey the processing key to delete (no-op if null)
     */
    public void deleteProcessingKey(String processingKey) {
        if (processingKey != null) {
            redisTemplate.delete(processingKey);
        }
    }

    /**
     * Converts raw Redis list elements into {@link AdImpressionDto}s, logging a
     * warning for any element that did not deserialize back to that type instead
     * of silently dropping it.
     */
    private List<AdImpressionDto> toImpressionDtos(List<Object> objects, String sourceKey) {
        if (objects == null || objects.isEmpty()) {
            return List.of();
        }

        List<AdImpressionDto> result = new ArrayList<>(objects.size());
        int dropped = 0;
        for (Object obj : objects) {
            if (obj instanceof AdImpressionDto dto) {
                result.add(dto);
            } else {
                dropped++;
                log.warn("Dropping non-AdImpressionDto element from {} (type={})",
                        sourceKey, obj == null ? "null" : obj.getClass().getName());
            }
        }
        if (dropped > 0) {
            log.warn("Dropped {} undeserializable impression(s) while popping {}", dropped, sourceKey);
        }
        return result;
    }

    /**
     * Snapshot of impressions claimed from Redis for processing, paired with the
     * Redis processing key holding them so the caller can delete it once the
     * impressions are durably persisted.
     */
    public record PoppedImpressions(String processingKey, List<AdImpressionDto> impressions) {
        public static PoppedImpressions empty() {
            return new PoppedImpressions(null, List.of());
        }

        public boolean isEmpty() {
            return impressions.isEmpty();
        }
    }

    /**
     * Retrieves all ad impressions for a specific ad.
     *
     * @param adId the ID of the ad
     * @return list of ad impressions for the given ad ID
     */
    public List<AdImpression> getByAdId(Long adId) {
        if (adId == null) {
            log.warn("Attempted to retrieve impressions with null ad ID");
            return List.of();
        }

        log.debug("Retrieving impressions for ad ID: {}", adId);
        return adImpressionRepository.findByAd_Id(adId);
    }

    /**
     * Deletes all ad impressions for a specific ad.
     *
     * @param adId the ID of the ad whose impressions should be deleted
     */
    @Transactional
    public void deleteByAdId(Long adId) {
        if (adId == null) {
            log.warn("Attempted to delete impressions with null ad ID");
            return;
        }

        log.info("Deleting all impressions for ad ID: {}", adId);
        adImpressionRepository.deleteByAd_Id(adId);
        log.info("Successfully deleted impressions for ad ID: {}", adId);
    }

    /**
     * Creates multiple ad impressions from DTOs.
     *
     * @param impressionDtos list of ad impression DTOs
     * @return list of created ad impressions
     */
    @Transactional
    public List<AdImpression> createAdImpressions(List<AdImpressionDto> impressionDtos) {
        if (impressionDtos == null || impressionDtos.isEmpty()) {
            log.warn("Attempted to create impressions with null or empty list");
            return List.of();
        }

        log.info("Creating {} ad impressions", impressionDtos.size());

        // Extract unique ad IDs
        Set<Long> adIds = impressionDtos.stream()
                .map(AdImpressionDto::getAdId)
                .collect(Collectors.toSet());

        // Fetch all ads in batch
        Map<Long, Ad> adsById = adService.findAllById(adIds).stream()
                .collect(Collectors.toMap(Ad::getId, ad -> ad));

        // Build impressions
        List<AdImpression> impressions = impressionDtos.stream()
                .map(dto -> {
                    Ad ad = adsById.get(dto.getAdId());
                    if (ad == null) {
                        throw new RuntimeException("Ad not found with ID: " + dto.getAdId());
                    }

                    return AdImpression.builder()
                            .ad(ad)
                            .timestamp(dto.getTimestamp())
                            .ipAddress(dto.getIpAddress())
                            .userId(dto.getUserId())
                            .build();
                })
                .collect(Collectors.toList());

        // Save all impressions in batch
        List<AdImpression> savedImpressions = adImpressionRepository.saveAll(impressions);
        log.info("Successfully created {} ad impressions", savedImpressions.size());

        return savedImpressions;
    }
}
