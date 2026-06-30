package com.example.adsportalbe.models.ad;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ad_impressions", indexes = {
        @Index(name = "idx_ad_impression_ad_id", columnList = "ad_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdImpression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_id", nullable = false)
    private Ad ad;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_id")
    private Long userId;
}
