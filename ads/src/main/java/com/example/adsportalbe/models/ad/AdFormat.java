package com.example.adsportalbe.models.ad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "ad_formats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdFormat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdFormatType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Double pricePerMille;

    private Boolean recommended;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ad_format_features", joinColumns = @JoinColumn(name = "ad_format_id"))
    @Column(name = "feature")
    private List<String> features;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "text_pricing_tiers", joinColumns = @JoinColumn(name = "ad_format_id"))
    private List<TextPricingTierRule> textPricingTiers;
}
