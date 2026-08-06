package com.mk3.chatapp.dtos.responses;

/**
 * Active promoted-message counts and amounts for a room, shown to the staff
 * member confirming an archive: pending holds are released and approved
 * payments refunded when the room is archived.
 */
public record RoomPromotionsSummaryDTO(
        int pendingCount,
        int approvedCount,
        double pendingReleaseTotal,
        double approvedRefundTotal,
        String currency
) {
}
