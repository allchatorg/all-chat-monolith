package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.promotion.*;
import com.example.adsportalbe.enums.PromotedMessageStatus;
import com.mk3.chatapp.models.identity.User;
import com.stripe.exception.StripeException;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.Map;

public interface PromotedMessageService {

    PromotedMessageDetailDto promoteMessage(PromoteMessageRequestDto request, User user) throws StripeException;

    Page<PromotedMessageDto> getUserPromotions(User user, PromotedMessageStatus status, int page, int size);

    PromotedMessageDetailDto getById(Long id, User user);

    PromotedMessageDetailDto cancelByUser(Long id, User user) throws StripeException;

    /**
     * Flags a PENDING promotion with an owner-submitted cancellation request
     * (reason required). No payment action and no status change — an admin
     * reviews the request and cancels/denies/approves as usual.
     */
    PromotedMessageDetailDto requestCancelByUser(Long id, String reason, User user);

    Page<PromotedMessageDto> searchPromotions(PromotedMessageSearchRequestDto request);

    PromotedMessageDetailDto approve(Long id) throws StripeException;

    PromotedMessageDetailDto deny(Long id, String reason) throws StripeException;

    PromotedMessageDetailDto cancelByAdmin(Long id, String reason) throws StripeException;

    BanPromotionsSummaryDto getBanPromotionsSummary(Long userId);

    PromotedRevenueSummaryDto getRevenueSummary();

    PromotedRevenueDailyResponseDto getDailyRevenue(java.time.LocalDate fromDate);

    PromotionSpendSummaryDto getSpendSummary(User user);

    /**
     * Cancels every PENDING (hold released) and APPROVED (payment refunded)
     * promotion of the user, marking each CANCELED by SYSTEM_BAN. Per-item
     * failures are logged and skipped, never thrown.
     */
    PromotionCancelOutcome cancelPromotionsForBannedUser(Long userId);

    /**
     * Cancels the active (PENDING or APPROVED) promotion on a message that is
     * being removed from chat: a PENDING hold is released, an APPROVED payment
     * is NOT refunded. No-op when the message has no active promotion.
     */
    void cancelForMessageRemoval(Long messageId, boolean removedByStaff) throws StripeException;

    /**
     * Cancels the user's active promotions whose messages were created after
     * the cutoff (i.e. deleted by a ban's delete-messages pass). Same
     * moderation semantics as {@link #cancelForMessageRemoval}; per-item
     * failures are logged and skipped, never thrown.
     */
    PromotionCancelOutcome cancelPromotionsForDeletedUserMessages(Long userId, java.time.Instant cutoff);

    Map<Long, ActivePromotion> getActivePromotions(Collection<Long> messageIds);

    Page<Long> getApprovedPromotedMessageIds(Long roomId, int page, int size);

    record PromotionCancelOutcome(int attempted, int released, int refunded, double totalReturned, String currency) {
    }

    record ActivePromotion(Long id, PromotedMessageStatus status) {
    }
}
