package com.example.adsportalbe.repositories;

import com.example.adsportalbe.enums.PromotedMessageStatus;
import com.example.adsportalbe.models.promotion.PromotedMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PromotedMessageRepository
        extends JpaRepository<PromotedMessage, Long>, JpaSpecificationExecutor<PromotedMessage> {

    @Query("SELECT pm.message.id FROM PromotedMessage pm " +
            "WHERE pm.chatRoomId = :roomId AND pm.status = com.example.adsportalbe.enums.PromotedMessageStatus.APPROVED " +
            "ORDER BY pm.approvedAt DESC")
    Page<Long> findApprovedMessageIdsByRoom(@Param("roomId") Long roomId, Pageable pageable);

    boolean existsByMessage_IdAndStatusIn(Long messageId, Collection<PromotedMessageStatus> statuses);

    List<PromotedMessage> findByMessage_IdInAndStatusIn(Collection<Long> messageIds,
                                                        Collection<PromotedMessageStatus> statuses);

    List<PromotedMessage> findByOwner_IdAndStatusIn(Long ownerId, Collection<PromotedMessageStatus> statuses);

    List<PromotedMessage> findByOwner_Id(Long ownerId);

    Page<PromotedMessage> findByOwner_Id(Long ownerId, Pageable pageable);

    Page<PromotedMessage> findByOwner_IdAndStatus(Long ownerId, PromotedMessageStatus status, Pageable pageable);

    long countByStatus(PromotedMessageStatus status);

    long countByOwner_IdAndStatus(Long ownerId, PromotedMessageStatus status);

    @Query("SELECT COALESCE(SUM(pm.amount), 0) FROM PromotedMessage pm WHERE pm.status = :status")
    double sumAmountByStatus(@Param("status") PromotedMessageStatus status);

    @Query("SELECT COALESCE(SUM(pm.amount), 0) FROM PromotedMessage pm " +
            "WHERE pm.owner.id = :ownerId AND pm.status = :status")
    double sumAmountByOwnerIdAndStatus(@Param("ownerId") Long ownerId,
                                       @Param("status") PromotedMessageStatus status);

    // Total actually charged to the owner: captured receipts, regardless of the
    // promotion's later status (admin-canceled APPROVED promotions are not refunded)
    @Query("SELECT COALESCE(SUM(r.amountPaid), 0) FROM PromotedMessage pm JOIN pm.receipt r " +
            "WHERE pm.owner.id = :ownerId AND r.status = 'CAPTURED'")
    double sumCapturedSpendByOwnerId(@Param("ownerId") Long ownerId);
}
