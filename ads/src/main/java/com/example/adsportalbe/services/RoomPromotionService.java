package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.promotion.PromotedRevenueDailyResponseDto;
import com.example.adsportalbe.dto.promotion.PromotedRevenueSummaryDto;
import com.example.adsportalbe.dto.roompromotion.*;
import com.example.adsportalbe.enums.RoomPromotionStatus;
import com.mk3.chatapp.models.identity.User;
import com.stripe.exception.StripeException;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface RoomPromotionService {

    RoomPromotionDetailDto promoteRoom(PromoteRoomRequestDto request, User user) throws StripeException;

    Page<RoomPromotionDto> getUserPromotions(User user, RoomPromotionStatus status, int page, int size);

    RoomPromotionDetailDto getById(Long id, User user);

    /** Platform-wide captured room-promotion revenue for the admin dashboard cards (super admin). */
    PromotedRevenueSummaryDto getRevenueSummary();

    /** Daily captured room-promotion revenue for the dedicated dashboard chart (super admin). */
    PromotedRevenueDailyResponseDto getDailyRevenue(LocalDate fromDate);


    /**
     * Flags a PENDING promotion with an owner-submitted cancellation request
     * (reason required). No payment action and no status change — an admin
     * reviews the request and cancels/denies/approves as usual.
     */
    RoomPromotionDetailDto requestCancelByUser(Long id, String reason, User user);

    Page<RoomPromotionDto> searchPromotions(RoomPromotionSearchRequestDto request);

    RoomPromotionDetailDto approve(Long id) throws StripeException;

    RoomPromotionDetailDto deny(Long id, String reason) throws StripeException;

    RoomPromotionDetailDto cancelByAdmin(Long id, String reason) throws StripeException;

    BanRoomPromotionsSummaryDto getBanPromotionsSummary(Long userId);

    /**
     * Releases every PENDING hold of the user on permanent ban, marking each
     * CANCELED by SYSTEM_BAN. APPROVED captures are NOT refunded. Per-item
     * failures are logged and skipped, never thrown.
     */
    PromotionCancelOutcome cancelPromotionsForBannedUser(Long userId);

    /**
     * Cancels every active (PENDING or APPROVED) promotion of a room being
     * archived, marking each CANCELED by ADMIN. Archiving is a platform
     * decision, not a moderation one, so APPROVED payments ARE refunded;
     * PENDING holds are released. Per-item failures are logged and skipped,
     * never thrown.
     */
    PromotionCancelOutcome cancelPromotionsForArchivedRoom(Long roomId);

    RoomPromotionsSummary getRoomPromotionsSummary(Long roomId);

    /**
     * One row per room with at least one APPROVED promotion, most recently
     * approved first; archived rooms excluded.
     */
    Page<PromotedRoomRowDto> getPromotedRooms(int page, int size);

    /**
     * {@code canceledWithoutRefund} counts APPROVED promotions older than the
     * archive refund window that were canceled but kept their captured payment.
     */
    record PromotionCancelOutcome(int attempted, int released, int refunded, double totalReturned, String currency,
                                  int canceledWithoutRefund) {
    }

    /**
     * {@code approvedRefundTotal} covers only APPROVED promotions inside the
     * archive refund window; the rest is reported as {@code approvedNonRefundable*}.
     */
    record RoomPromotionsSummary(int pendingCount, int approvedCount,
                                 int approvedRefundableCount, int approvedNonRefundableCount,
                                 double pendingReleaseTotal, double approvedRefundTotal,
                                 double approvedNonRefundableTotal, String currency) {
    }
}
