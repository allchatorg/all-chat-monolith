package com.example.adsportalbe.models.ad;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A recorded click-through on a photo/video ad (the chat user opened the ad's
 * media overlay). Deduplicated per user per ad per UTC day via the unique
 * constraint — repeat opens the same day are not recorded again.
 */
@Entity
@Table(name = "ad_clicks", indexes = {
        @Index(name = "idx_ad_click_ad_id", columnList = "ad_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ad_id", "user_id", "click_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_id", nullable = false)
    private Ad ad;

    @Column(nullable = false)
    private Instant timestamp;

    // UTC day of the click, same day boundary as AdDailyStatistics.date
    @Column(name = "click_date", nullable = false)
    private LocalDate clickDate;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
