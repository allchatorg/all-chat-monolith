package com.example.adsportalbe.services;

import com.mk3.chatapp.dtos.responses.ServedAdDto;
import com.mk3.chatapp.services.AdServingPort;
import lombok.RequiredArgsConstructor;
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
}
