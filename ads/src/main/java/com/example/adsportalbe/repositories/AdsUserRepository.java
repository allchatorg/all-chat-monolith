package com.example.adsportalbe.repositories;

import com.mk3.chatapp.models.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdsUserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT new com.example.adsportalbe.dto.AdminUserDto(" +
            "u.id, u.firstName, u.lastName, u.email, u.role, " +
            "COUNT(a), SUM(a.totalCost), u.createdAt) " +
            "FROM User u LEFT JOIN Ad a ON a.owner = u " +
            "WHERE u.id = :id " +
            "GROUP BY u.id")
    Optional<com.example.adsportalbe.dto.AdminUserDto> findAdminUserById(
            @org.springframework.data.repository.query.Param("id") Long id);
}
