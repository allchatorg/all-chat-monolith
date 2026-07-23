package com.example.adsportalbe.models.ad;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A recorded click on a specific hyperlink inside an ad's text content (any
 * format, including TEXT ads). Tracked separately from {@link AdClick}
 * media click-throughs. Deduplicated per user per link per UTC day via the
 * unique constraint — repeat clicks the same day are not recorded again.
 */
@Entity
@Table(name = "ad_hyperlink_clicks", indexes = {
        @Index(name = "idx_ad_hyperlink_click_ad_id", columnList = "ad_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ad_id", "link_url", "user_id", "click_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdHyperlinkClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_id", nullable = false)
    private Ad ad;

    // The URL exactly as it appears in the ad's textContent; capped at 1024 so
    // the composite unique btree index stays under Postgres's row-size limit.
    @Column(name = "link_url", nullable = false, length = 1024)
    private String linkUrl;

    @Column(nullable = false)
    private Instant timestamp;

    // UTC day of the click, same day boundary as AdClick.clickDate
    @Column(name = "click_date", nullable = false)
    private LocalDate clickDate;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
