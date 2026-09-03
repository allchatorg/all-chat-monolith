package com.example.adsportalbe.services;

import com.mk3.chatapp.services.RoomPromotionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * In-process implementation of the chat module's {@link RoomPromotionPort},
 * mirroring {@link MessagePromotionAdapter}. Keeps the dependency direction
 * {@code ads -> chat}.
 */
@Service
@RequiredArgsConstructor
public class RoomPromotionAdapter implements RoomPromotionPort {

    private final RoomPromotionService roomPromotionService;

    @Override
    public Page<PromotedRoom> getPromotedRooms(int page, int size) {
        return roomPromotionService.getPromotedRooms(page, size)
                .map(row -> new PromotedRoom(row.roomId(), row.promotedAt()));
    }

    @Override
    public RoomPromotionOutcome cancelPromotionsForBannedUser(Long userId) {
        return toOutcome(roomPromotionService.cancelPromotionsForBannedUser(userId));
    }

    @Override
    public RoomPromotionOutcome cancelActivePromotionsForRoom(Long roomId) {
        return toOutcome(roomPromotionService.cancelPromotionsForArchivedRoom(roomId));
    }

    @Override
    public RoomPromotionsSummary getRoomPromotionsSummary(Long roomId) {
        RoomPromotionService.RoomPromotionsSummary summary = roomPromotionService.getRoomPromotionsSummary(roomId);
        return new RoomPromotionsSummary(
                summary.pendingCount(),
                summary.approvedCount(),
                summary.approvedRefundableCount(),
                summary.approvedNonRefundableCount(),
                summary.pendingReleaseTotal(),
                summary.approvedRefundTotal(),
                summary.approvedNonRefundableTotal(),
                summary.currency());
    }

    private static RoomPromotionOutcome toOutcome(RoomPromotionService.PromotionCancelOutcome outcome) {
        return new RoomPromotionOutcome(
                outcome.attempted(),
                outcome.released(),
                outcome.refunded(),
                outcome.totalReturned(),
                outcome.currency(),
                outcome.canceledWithoutRefund());
    }
}
