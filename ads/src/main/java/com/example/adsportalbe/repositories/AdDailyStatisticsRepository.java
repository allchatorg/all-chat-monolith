package com.example.adsportalbe.repositories;

import com.example.adsportalbe.models.ad.AdDailyStatistics;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AdDailyStatisticsRepository extends JpaRepository<AdDailyStatistics, Long> {
    Optional<AdDailyStatistics> findByAdIdAndDate(Long adId, LocalDate date);

    @Query("SELECT ads FROM AdDailyStatistics ads WHERE ads.ad.id IN :adIds AND ads.date IN :dates")
    List<AdDailyStatistics> findByAdIdInAndDateIn(@Param("adIds") Set<Long> adIds,
                                                  @Param("dates") Set<LocalDate> dates);

    List<AdDailyStatistics> findByAdIdOrderByDateDesc(Long adId);

    @Query("SELECT ads FROM AdDailyStatistics ads WHERE ads.ad.id = :adId AND ads.date >= :fromDate ORDER BY ads.date DESC")
    List<AdDailyStatistics> findByAdIdAndDateFrom(@Param("adId") Long adId, @Param("fromDate") LocalDate fromDate);

    @Query("SELECT ads FROM AdDailyStatistics ads WHERE ads.ad.owner.id = :ownerId AND ads.date IN :dates")
    List<AdDailyStatistics> findByOwnerIdAndDateIn(@Param("ownerId") Long ownerId,
                                                   @Param("dates") Set<LocalDate> dates);

    @Query("SELECT ads FROM AdDailyStatistics ads WHERE ads.ad.owner.id = :ownerId ORDER BY ads.date DESC")
    List<AdDailyStatistics> findByOwnerIdOrderByDateDesc(@Param("ownerId") Long ownerId);

    @Query("SELECT ads FROM AdDailyStatistics ads WHERE ads.ad.owner.id = :ownerId AND ads.date >= :fromDate ORDER BY ads.date DESC")
    List<AdDailyStatistics> findByOwnerIdAndDateFrom(@Param("ownerId") Long ownerId,
                                                     @Param("fromDate") LocalDate fromDate);
}
