package com.mk3.chatapp.configs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.capybara.clamav.ClamavClient;

@Slf4j
@Configuration
public class ClamAVConfig {

    @Bean
    public ClamavClient clamavClient(
            @Value("${clamav.host}") String host,
            @Value("${clamav.port}") int port) {
        log.info("ClamAV host: {}, port: {}", host, port);
        return new ClamavClient(host, port);
    }
}
