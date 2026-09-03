package com.mk3.chatapp.services;

import org.springframework.data.domain.Page;

import java.time.Instant;

/**
 * SPI for the room-promotions feature owned by the ads module, mirroring
 * {@link MessagePromotionPort}: defined in the chat module so chat depends
 * only on this interface — the dependency direction stays {@code ads -> chat}
 * (chat never imports ads). The ads module supplies the bean. Status never
 * crosses this boundary as the ads enum.
 */
public interface RoomPromotionPort {

    /**
     * Rooms with at least one APPROVED promotion, most recently approved
     * first; archived rooms excluded. One row per room.
     */
    Page<PromotedRoom> getPromotedRooms(int page, int size);

    /**
     * Releases every PENDING hold of the user on permanent ban (APPROVED
     * captures are NOT refunded). Per-item failures are caught and logged by
     * the implementation so one failure never aborts the others.
     */
    RoomPromotionOutcome cancelPromotionsForBannedUser(Long userId);

    /**
     * Cancels every active promotion of a room being archived: PENDING holds
     * are released and APPROVED payments ARE refunded (archiving is a platform
     * decision, not a moderation one). Per-item failures are caught and logged.
     */
    RoomPromotionOutcome cancelActivePromotionsForRoom(Long roomId);

    /**
     * Counts and amounts of the room's active (PENDING or APPROVED) room
     * promotions — shown to the staff member before they archive the room.
     */
    RoomPromotionsSummary getRoomPromotionsSummary(Long roomId);

    record PromotedRoom(Long roomId, Instant promotedAt) {
    }

    /**
     * Archiving a room refunds an APPROVED room promotion only when it was
     * approved (= charged) within this many hours; older ones are canceled
     * without a refund. Single source of truth for the ads service and the DTO.
     */
    int ARCHIVE_REFUND_WINDOW_HOURS = 24;

    record RoomPromotionOutcome(int attempted, int released, int refunded, double totalReturned, String currency,
                                int canceledWithoutRefund) {
    }

    record RoomPromotionsSummary(int pendingCount, int approvedCount,
                                 int approvedRefundableCount, int approvedNonRefundableCount,
                                 double pendingReleaseTotal, double approvedRefundTotal,
                                 double approvedNonRefundableTotal, String currency) {
    }
}
