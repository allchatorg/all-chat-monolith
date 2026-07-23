package com.example.adsportalbe.repositories;

import com.example.adsportalbe.models.ad.AdHyperlinkClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AdHyperlinkClickRepository extends JpaRepository<AdHyperlinkClick, Long> {

    boolean existsByAd_IdAndLinkUrlAndUserIdAndClickDate(Long adId, String linkUrl, Long userId, LocalDate clickDate);

    // Dedup caps the raw table at one row per user per link per day, so this
    // GROUP BY is the daily aggregate — no mirror daily-stats table needed.
    interface LinkDailyCount {
        String getLinkUrl();

        LocalDate getClickDate();

        Long getClicks();
    }

    @Query("SELECT c.linkUrl AS linkUrl, c.clickDate AS clickDate, COUNT(c) AS clicks " +
            "FROM AdHyperlinkClick c WHERE c.ad.id = :adId " +
            "GROUP BY c.linkUrl, c.clickDate ORDER BY c.clickDate DESC")
    List<LinkDailyCount> countDailyByAdId(@Param("adId") Long adId);
}
