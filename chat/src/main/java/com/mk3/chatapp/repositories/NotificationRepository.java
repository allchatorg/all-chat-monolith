package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUser_Id(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_IdAndReadAtIsNull(Long userId);

    // @Where does not apply to JPQL bulk updates, hence the explicit deleted check
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null and n.deleted = false")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("select n.user.id from Notification n where n.type = :type and n.createdAt >= :since")
    Set<Long> findUserIdsNotifiedSince(@Param("type") com.mk3.chatapp.enums.NotificationType type,
                                       @Param("since") Instant since);
}
