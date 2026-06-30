package com.example.adsportalbe.bootstrap;

import com.example.adsportalbe.models.ad.AdFormat;
import com.example.adsportalbe.models.ad.AdFormatType;
import com.example.adsportalbe.repositories.AdFormatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AdFormatRepository adFormatRepository;

    @Override
    public void run(String... args) {
        try {
            log.info("Starting DataSeeding...");
            // User seeding lives in the chat module's BootstrapDev/UserCreationService — both
            // modules share the unified chat_user table, so seeding users here too races with
            // BootstrapDev's isFirstStart() check and suppresses the john/jane/alice seed.
            seedAdFormats();
            log.info("DataSeeding completed successfully.");
        } catch (Exception e) {
            log.error("DataSeeding failed: {}", e.getMessage(), e);
        }
    }

    private void seedAdFormats() {
        if (adFormatRepository.count() == 0) {
            log.info("Seeding AdFormats...");
            List<AdFormat> formats = new ArrayList<>();

            formats.add(AdFormat.builder()
                    .type(AdFormatType.TEXT)
                    .title("Text Ad")
                    .description("Simple text-based advertisement.")
                    .pricePerMille(2.0)
                    .recommended(false)
                    .build());

            formats.add(AdFormat.builder()
                    .type(AdFormatType.PHOTO)
                    .title("Photo Ad")
                    .description("Image-based advertisement.")
                    .pricePerMille(5.0)
                    .recommended(true)
                    .features(List.of("1 Image", "Caption"))
                    .build());

            formats.add(AdFormat.builder()
                    .type(AdFormatType.VIDEO)
                    .title("Video Ad")
                    .description("Video-based advertisement.")
                    .pricePerMille(10.0)
                    .recommended(false)
                    .build());

            adFormatRepository.saveAll(formats);
        }
    }
}
