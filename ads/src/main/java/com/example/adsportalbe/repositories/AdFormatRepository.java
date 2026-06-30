package com.example.adsportalbe.repositories;

import com.example.adsportalbe.models.ad.AdFormat;
import com.example.adsportalbe.models.ad.AdFormatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdFormatRepository extends JpaRepository<AdFormat, Long> {
    Optional<AdFormat> findByType(AdFormatType type);
}
