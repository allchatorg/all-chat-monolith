package com.example.adsportalbe.repositories;

import com.example.adsportalbe.models.ad.AdClick;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface AdClickRepository extends JpaRepository<AdClick, Long> {

    long countByAd_Id(Long adId);

    boolean existsByAd_IdAndUserIdAndClickDate(Long adId, Long userId, LocalDate clickDate);

    @Query("SELECT COUNT(c) FROM AdClick c WHERE c.ad.owner.id = :ownerId")
    long countByOwnerId(@Param("ownerId") Long ownerId);
}
