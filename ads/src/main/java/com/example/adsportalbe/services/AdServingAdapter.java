package com.example.adsportalbe.services;

import com.mk3.chatapp.dtos.responses.ServedAdDto;
import com.mk3.chatapp.services.AdServingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * In-process implementation of the chat module's {@link AdServingPort}. In the
 * merged monolith the chat ad-serving no longer makes an HTTP call to a separate
 * ad service — it delegates straight to the ads module's {@link AdStatisticsService}.
 *
 * <p>Maps the ads-side {@code ServedAdDto} onto the chat-side {@code ServedAdDto}
 * (identical fields, different package). The dependency direction stays
 * {@code ads -> chat}: ads implements the chat-defined interface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdServingAdapter implements AdServingPort {

    private final AdStatisticsService adStatisticsService;

    @Override
    public ServedAdDto serveAd(Long userId, String ipAddress) {
        com.example.adsportalbe.dto.ServedAdDto served = adStatisticsService.serveAd(userId, ipAddress);
        if (served == null) {
            return null;
        }
        return ServedAdDto.builder()
                .id(served.getId())
                .title(served.getTitle())
                .textContent(served.getTextContent())
                .imageUrl(served.getImageUrl())
                .videoUrl(served.getVideoUrl())
                .format(served.getFormat())
                .build();
    }

    @Override
    public void registerClick(Long adId, Long userId, String ipAddress) {
        try {
            adStatisticsService.registerClick(adId, userId, ipAddress);
        } catch (DataIntegrityViolationException e) {
            // Constraint race: either the impression job upserted the same
            // (ad_id, date) daily-stats row concurrently, or a duplicate click hit
            // the (ad_id, user_id, click_date) constraint. One retry in a fresh
            // transaction resolves both — the exists/find checks now see the rows.
            try {
                adStatisticsService.registerClick(adId, userId, ipAddress);
            } catch (DataIntegrityViolationException retryFailure) {
                log.warn("Dropping click for ad {} by user {} after constraint race retry", adId, userId,
                        retryFailure);
            }
        }
    }

    @Override
    public void registerLinkClick(Long adId, String url, Long userId, String ipAddress) {
        try {
            adStatisticsService.registerLinkClick(adId, url, userId, ipAddress);
        } catch (DataIntegrityViolationException e) {
            // Duplicate-click race on the (ad_id, link_url, user_id, click_date)
            // constraint. One retry in a fresh transaction resolves it — the
            // exists check now sees the row and no-ops.
            try {
                adStatisticsService.registerLinkClick(adId, url, userId, ipAddress);
            } catch (DataIntegrityViolationException retryFailure) {
                log.warn("Dropping link click for ad {} by user {} after constraint race retry", adId, userId,
                        retryFailure);
            }
        }
    }
}
