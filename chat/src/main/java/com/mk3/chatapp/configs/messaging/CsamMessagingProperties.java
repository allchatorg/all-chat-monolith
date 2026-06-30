package com.mk3.chatapp.configs.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "csam.messaging")
public record CsamMessagingProperties(
        boolean enabled,
        String exchange,
        String analysisRequestQueue,
        String analysisRequestRoutingKey,
        String analysisResultQueue,
        String analysisResultRoutingKey) {
}
