package com.mk3.chatapp.services;

import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.Map;

/**
 * SPI for the promoted-messages feature owned by the ads module, mirroring
 * {@link AdsModerationPort}: defined in the chat module so chat depends only
 * on this interface — the dependency direction stays {@code ads -> chat}
 * (chat never imports ads), avoiding a module cycle. The ads module supplies
 * the bean.
 */
public interface MessagePromotionPort {

    /**
     * Cancels the active (PENDING or APPROVED) promotion on a message that is
     * being removed from chat: a PENDING hold is released, an APPROVED payment
     * is NOT refunded. No-op when the message has no active promotion. Throws
     * on payment-provider failure so the caller can abort the removal.
     */
    void cancelActivePromotionForMessage(Long messageId, boolean removedByStaff) throws Exception;

    /**
     * Active (PENDING or APPROVED) promotions for the given message ids, keyed
     * by message id. One batched query per page — used to enrich message DTOs.
     */
    Map<Long, PromotionInfo> getActivePromotions(Collection<Long> messageIds);

    /**
     * Ids of APPROVED promoted messages in the room, ordered by approvedAt desc.
     */
    Page<Long> getApprovedPromotedMessageIds(Long roomId, int page, int size);

    /**
     * Cancels every PENDING (hold released) and APPROVED (payment refunded)
     * promotion of the user on permanent ban. Per-item failures are caught and
     * logged by the implementation so one failure never aborts the others.
     */
    PromotionBanOutcome cancelPromotionsForBannedUser(Long userId);

    /**
     * Cancels the user's active promotions whose messages were created after
     * the cutoff (i.e. removed by a ban's delete-messages pass): pending holds
     * are released, approved payments are NOT refunded. Per-item failures are
     * caught and logged by the implementation.
     */
    PromotionBanOutcome cancelPromotionsForDeletedUserMessages(Long userId, java.time.Instant cutoff);

    /**
     * Counts and amounts of the room's active (PENDING or APPROVED)
     * promotions — shown to the staff member before they archive the room.
     */
    RoomPromotionsSummary getRoomPromotionsSummary(Long roomId);

    /**
     * Cancels every active promotion in a room being archived. Archiving is a
     * platform decision rather than a moderation one, so unlike the other
     * cancel paths APPROVED payments ARE refunded; PENDING holds are released.
     * Owners are notified. Per-item failures are caught and logged by the
     * implementation so one failure never aborts the others.
     */
    PromotionBanOutcome cancelActivePromotionsForRoom(Long roomId);

    record PromotionInfo(Long id, String status) {
    }

    record RoomPromotionsSummary(int pendingCount, int approvedCount,
                                 double pendingReleaseTotal, double approvedRefundTotal, String currency) {
    }

    record PromotionBanOutcome(int attempted, int released, int refunded, double totalReturned, String currency) {
    }
}
