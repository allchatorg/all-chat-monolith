package com.example.adsportalbe.repositories;

import com.example.adsportalbe.models.identity.AdminUserSummary;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdsUserRepository extends org.springframework.data.repository.Repository<AdminUserSummary, Long>,
        JpaSpecificationExecutor<AdminUserSummary> {
    Optional<AdminUserSummary> findById(Long id);
}
