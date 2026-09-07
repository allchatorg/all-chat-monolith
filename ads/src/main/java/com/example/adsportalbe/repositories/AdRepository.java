package com.example.adsportalbe.repositories;

import com.example.adsportalbe.dto.ad.AdStatusCountDto;
import com.example.adsportalbe.enums.AdStatus;
import com.example.adsportalbe.models.ad.Ad;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface AdRepository extends JpaRepository<Ad, Long>, JpaSpecificationExecutor<Ad> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Ad a WHERE a.id = :id")
    Optional<Ad> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Ad a WHERE a.id IN :ids ORDER BY a.id")
    List<Ad> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Ad a WHERE a.owner.id = :userId AND a.status = com.example.adsportalbe.enums.AdStatus.PENDING AND a.receipt.status = 'AUTHORIZED' ORDER BY a.id")
    List<Ad> findPendingRefundableAdsForUpdate(@Param("userId") Long userId);

    @Query("SELECT new com.example.adsportalbe.dto.ad.AdStatusCountDto(a.status, COUNT(a)) FROM Ad a GROUP BY a.status")
    List<AdStatusCountDto> getAdStatusCounts();

    @Query("SELECT new com.example.adsportalbe.dto.ad.AdStatusCountDto(a.status, COUNT(a)) FROM Ad a WHERE a.owner.id = :userId GROUP BY a.status")
    List<AdStatusCountDto> getAdStatusCountsByUserId(@Param("userId") Long userId);

    List<Ad> findAllByOwnerId(Long ownerId);

    @Query("SELECT a FROM Ad a WHERE a.owner.id = :userId AND a.status = com.example.adsportalbe.enums.AdStatus.PENDING AND a.receipt.status = 'AUTHORIZED'")
    List<Ad> findPendingRefundableAdsByOwnerId(@Param("userId") Long userId);

    @Query("SELECT a FROM Ad a WHERE a.approvedAt IS NOT NULL AND a.approvedAt >= :fromDate AND a.approvedAt <= :toDate ORDER BY a.approvedAt ASC")
    List<Ad> findApprovedAdsBetweenDates(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate);

    List<Ad> findAllByStatus(AdStatus status);
}
