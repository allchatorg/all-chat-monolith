package com.example.adsportalbe.repositories;

import com.example.adsportalbe.models.ad.AdImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdImpressionRepository extends JpaRepository<AdImpression, Long> {

    List<AdImpression> findByAd_Id(Long adId);

    void deleteByAd_Id(Long adId);
}
