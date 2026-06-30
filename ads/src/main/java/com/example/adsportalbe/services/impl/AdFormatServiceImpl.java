package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.dto.AdFormatDto;
import com.example.adsportalbe.models.ad.AdFormat;
import com.example.adsportalbe.models.ad.AdFormatType;
import com.example.adsportalbe.models.ad.TextPricingTierRule;
import com.example.adsportalbe.repositories.AdFormatRepository;
import com.example.adsportalbe.services.AdFormatService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdFormatServiceImpl implements AdFormatService {

    private final AdFormatRepository adFormatRepository;

    @Override
    public List<AdFormatDto> getAllFormats() {
        return adFormatRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public AdFormatDto getFormatById(Long id) {
        return adFormatRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("Ad Format not found"));
    }

    @Override
    public double calculateTextCPM(int textLength) {
        AdFormat textFormat = adFormatRepository.findAll().stream()
                .filter(f -> f.getType() == AdFormatType.TEXT)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Text Ad Format not found"));

        if (textFormat.getTextPricingTiers() == null || textFormat.getTextPricingTiers().isEmpty()) {
            return textFormat.getPricePerMille();
        }

        return textFormat.getTextPricingTiers().stream()
                .sorted(Comparator.comparingInt(TextPricingTierRule::getMaxCharacters))
                .filter(tier -> textLength <= tier.getMaxCharacters())
                .map(TextPricingTierRule::getPricePerMille)
                .findFirst()
                .orElse(textFormat.getTextPricingTiers().stream()
                        .max(Comparator.comparingInt(TextPricingTierRule::getMaxCharacters))
                        .map(TextPricingTierRule::getPricePerMille)
                        .orElse(0.0));
    }

    private AdFormatDto mapToDto(AdFormat adFormat) {
        return AdFormatDto.builder()
                .id(adFormat.getId())
                .type(adFormat.getType())
                .title(adFormat.getTitle())
                .description(adFormat.getDescription())
                .pricePerMille(adFormat.getPricePerMille())
                .recommended(adFormat.getRecommended())
                .features(adFormat.getFeatures())
                .pricingTiers(adFormat.getTextPricingTiers())
                .build();
    }

    @PostConstruct
    public void seedData() {
        if (adFormatRepository.count() == 0) {
            AdFormat textFormat = AdFormat.builder()
                    .type(AdFormatType.TEXT)
                    .title("Text Advertisement")
                    .description("Simple text-based advertisement")
                    .pricePerMille(0.0)
                    .recommended(false)
                    .features(List.of("Text-only"))
                    .textPricingTiers(List.of(
                            new TextPricingTierRule(125, 3.0),
                            new TextPricingTierRule(250, 4.0),
                            new TextPricingTierRule(500, 5.0)))
                    .build();

            AdFormat photoFormat = AdFormat.builder()
                    .type(AdFormatType.PHOTO)
                    .title("Display / Photo Ad")
                    .description("High-visibility visual format")
                    .pricePerMille(5.0)
                    .recommended(false)
                    .features(List.of("PNG/JPG support", "Combine with Text"))
                    .build();

            AdFormat videoFormat = AdFormat.builder()
                    .type(AdFormatType.VIDEO)
                    .title("Video Ad")
                    .description("Engaging video content")
                    .pricePerMille(5.0)
                    .recommended(true)
                    .features(List.of("MP4 support", "High conversion rate"))
                    .build();

            adFormatRepository.saveAll(List.of(textFormat, photoFormat, videoFormat));
        }
    }
}
