package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.enums.FontPreset;
import lombok.NonNull;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findById(Long id);

    // Read the billing projection directly, bypassing potentially stale authenticated entities.
    @Query("select u.proPaidThrough from User u where u.id = :id and u.deleted = false and u.banned = false")
    Optional<Instant> findEligibleProPaidThrough(@Param("id") Long id);

    @Query("select (count(u) > 0) from User u where u.id = :id and u.deleted = false and u.banned = false " +
            "and u.role in (com.mk3.chatapp.enums.Role.MODERATOR, com.mk3.chatapp.enums.Role.ADMIN, " +
            "com.mk3.chatapp.enums.Role.SUPER_ADMIN)")
    boolean hasStaffProAccess(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("update User u set u.usernameFont = :usernameFont, u.messageFont = :messageFont, " +
            "u.fontRevision = :revision, u.fontChangesDate = :changesDate, u.fontChangesCount = :changesCount where u.id = :id")
    int updateFontState(@Param("id") Long id, @Param("usernameFont") FontPreset usernameFont,
                        @Param("messageFont") FontPreset messageFont, @Param("revision") long revision,
                        @Param("changesDate") LocalDate changesDate, @Param("changesCount") int changesCount);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("update User u set u.proPaidThrough = :paidThrough, u.showProBadge = :showBadge, " +
            "u.proBadgeRevision = :revision, u.proBadgeLastPublishedVisible = :visible where u.id = :id")
    int updateProState(@Param("id") Long id, @Param("paidThrough") Instant paidThrough,
                       @Param("showBadge") boolean showBadge, @Param("revision") long revision,
                       @Param("visible") boolean visible);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update User u set u.stripeCustomerId = :customerId where u.id = :id")
    int updateStripeCustomerId(@Param("id") Long id, @Param("customerId") String customerId);

    List<User> findByIdIn(java.util.Collection<Long> ids);

    @Query("select u.id from User u where u.id > :afterId and (u.proBadgeLastPublishedVisible = true " +
            "or u.usernameFont <> com.mk3.chatapp.enums.FontPreset.DEFAULT " +
            "or u.messageFont <> com.mk3.chatapp.enums.FontPreset.DEFAULT) " +
            "and (u.deleted = true or ((u.proPaidThrough <= :now or u.proPaidThrough is null) " +
            "and u.role not in (com.mk3.chatapp.enums.Role.MODERATOR, com.mk3.chatapp.enums.Role.ADMIN, " +
            "com.mk3.chatapp.enums.Role.SUPER_ADMIN))) order by u.id")
    List<Long> findExpiredProBadgeUserIds(@Param("now") Instant now, @Param("afterId") Long afterId,
                                         org.springframework.data.domain.Pageable pageable);

    @NonNull
    List<User> findAll(Specification<User> spec);

    @Query("SELECT u FROM User u WHERE u.claimed = false AND u.lastSeen < :cutoffTime AND u.role IN ('GUEST', 'UNCLAIMED_USER')")
    List<User> findStaleGuestAndUnclaimedUsers(@Param("cutoffTime") Instant cutoffTime);

    boolean existsByPhoneNumber(String phoneNumber);

    /** Users whose effective zone (stored, or the fallback when unset) is one of the given zones. */
    @Query("SELECT u FROM User u WHERE u.deleted = false AND u.banned = false AND COALESCE(u.timeZone, :fallbackZone) IN :zones")
    List<User> findActiveUsersInTimeZones(@Param("zones") java.util.Collection<String> zones,
                                          @Param("fallbackZone") String fallbackZone);

    List<User> findByRoleIn(java.util.Collection<com.mk3.chatapp.enums.Role> roles);

    Optional<User> findByIdVerificationSessionId(String idVerificationSessionId);

    List<User> findByIdVerificationStatusAndVerifiedDateOfBirthLessThanEqual(
            com.mk3.chatapp.enums.IdVerificationStatus idVerificationStatus, java.time.LocalDate verifiedDateOfBirth);

}
