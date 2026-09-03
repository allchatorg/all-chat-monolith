package com.mk3.chatapp.dtos.responses;

/**
 * Active promotion counts and amounts for a room, shown to the staff member
 * confirming an archive. Promoted messages (first block): pending holds are
 * released and approved payments refunded. Room promotions ({@code roomPromotion*}):
 * pending holds are released; approved payments are refunded only when approved
 * within {@code roomPromotionRefundWindowHours}, older ones are canceled without
 * a refund ({@code roomPromotionApprovedNonRefundable*}).
 */
public record RoomPromotionsSummaryDTO(
        int pendingCount,
        int approvedCount,
        double pendingReleaseTotal,
        double approvedRefundTotal,
        String currency,
        int roomPromotionPendingCount,
        int roomPromotionApprovedCount,
        double roomPromotionPendingReleaseTotal,
        double roomPromotionApprovedRefundTotal,
        int roomPromotionApprovedRefundableCount,
        int roomPromotionApprovedNonRefundableCount,
        double roomPromotionApprovedNonRefundableTotal,
        int roomPromotionRefundWindowHours
) {
}
