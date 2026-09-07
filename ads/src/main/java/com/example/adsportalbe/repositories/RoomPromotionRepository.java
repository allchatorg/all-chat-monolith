package com.example.adsportalbe.repositories;

import com.example.adsportalbe.dto.roompromotion.PromotedRoomRowDto;
import com.example.adsportalbe.enums.RoomPromotionStatus;
import com.example.adsportalbe.models.promotion.RoomPromotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomPromotionRepository
        extends JpaRepository<RoomPromotion, Long>, JpaSpecificationExecutor<RoomPromotion> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rp FROM RoomPromotion rp WHERE rp.id = :id")
    Optional<RoomPromotion> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rp FROM RoomPromotion rp WHERE rp.owner.id = :ownerId AND rp.status IN :statuses ORDER BY rp.id")
    List<RoomPromotion> findByOwnerIdAndStatusInForUpdate(@Param("ownerId") Long ownerId,
                                                       @Param("statuses") Collection<RoomPromotionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rp FROM RoomPromotion rp WHERE rp.chatRoom.id = :roomId AND rp.status IN :statuses ORDER BY rp.id")
    List<RoomPromotion> findByChatRoomIdAndStatusInForUpdate(@Param("roomId") Long roomId,
                                                          @Param("statuses") Collection<RoomPromotionStatus> statuses);

    /**
     * One row per room with at least one APPROVED promotion, most recently
     * approved first; archived rooms excluded. Callers must pass an UNSORTED
     * {@link Pageable} — the ordering is part of the query.
     */
    @Query(value = """
            select new com.example.adsportalbe.dto.roompromotion.PromotedRoomRowDto(rp.chatRoom.id, max(rp.approvedAt))
            from RoomPromotion rp
            where rp.status = com.example.adsportalbe.enums.RoomPromotionStatus.APPROVED
              and rp.chatRoom.isArchived = false
            group by rp.chatRoom.id
            order by max(rp.approvedAt) desc
            """,
            countQuery = """
            select count(distinct rp.chatRoom.id) from RoomPromotion rp
            where rp.status = com.example.adsportalbe.enums.RoomPromotionStatus.APPROVED
              and rp.chatRoom.isArchived = false
            """)
    Page<PromotedRoomRowDto> findPromotedRooms(Pageable pageable);

    boolean existsByOwner_IdAndChatRoom_IdAndStatus(Long ownerId, Long chatRoomId, RoomPromotionStatus status);

    List<RoomPromotion> findByChatRoom_IdAndStatusIn(Long chatRoomId, Collection<RoomPromotionStatus> statuses);

    List<RoomPromotion> findByOwner_IdAndStatusIn(Long ownerId, Collection<RoomPromotionStatus> statuses);

    List<RoomPromotion> findByOwner_Id(Long ownerId);

    Page<RoomPromotion> findByOwner_Id(Long ownerId, Pageable pageable);

    Page<RoomPromotion> findByOwner_IdAndStatus(Long ownerId, RoomPromotionStatus status, Pageable pageable);

    long countByStatus(RoomPromotionStatus status);

    @Query("SELECT COALESCE(SUM(rp.amount), 0) FROM RoomPromotion rp WHERE rp.status = :status")
    double sumAmountByStatus(@Param("status") RoomPromotionStatus status);
}
