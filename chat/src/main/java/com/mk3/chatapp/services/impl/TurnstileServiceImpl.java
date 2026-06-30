package com.mk3.chatapp.services.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mk3.chatapp.services.TurnstileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurnstileServiceImpl implements TurnstileService {

    private final RestTemplate restTemplate;

    private final Environment environment;

    @Value("${turnstile.secret-key}")
    private String secretKey;

    @Value("${turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
    private String verifyUrl;

    @Override
    public boolean verify(String token, String ip) {
        if (environment.matchesProfiles("dev", "test")) {
            log.info("Skipping Turnstile verification in dev/test environment");
            return true;
        }

        if (token == null || token.isBlank()) {
            log.warn("Turnstile token is missing");
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("secret", secretKey);
        map.add("response", token);
        if (ip != null) {
            map.add("remoteip", ip);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            TurnstileResponse response = restTemplate.postForObject(verifyUrl, request, TurnstileResponse.class);
            if (response != null && !response.success()) {
                log.warn("Turnstile verification failed: {}", response.errorCodes());
            }
            return response != null && response.success();
        } catch (Exception e) {
            log.error("Error verifying Turnstile token", e);
            return false;
        }
    }

    private record TurnstileResponse(
            boolean success,
            @JsonProperty("challenge_ts") String challengeTs,
            String hostname,
            @JsonProperty("error-codes") List<String> errorCodes,
            String action,
            String cdata) {
    }
}
