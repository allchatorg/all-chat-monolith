package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.dto.payment.PaymentMethodDto;
import com.example.adsportalbe.dto.promotion.PromotedRevenueDailyResponseDto;
import com.example.adsportalbe.dto.promotion.PromotedRevenueSummaryDto;
import com.example.adsportalbe.dto.roompromotion.*;
import com.example.adsportalbe.enums.CanceledBy;
import com.example.adsportalbe.enums.PurchaseType;
import com.example.adsportalbe.enums.RoomPromotionStatus;
import com.example.adsportalbe.exceptions.ConflictException;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import com.example.adsportalbe.models.promotion.RoomPromotion;
import com.example.adsportalbe.repositories.PaymentReceiptRepository;
import com.example.adsportalbe.repositories.RoomPromotionRepository;
import com.example.adsportalbe.services.PaymentService;
import com.example.adsportalbe.services.RoomPromotionService;
import com.example.adsportalbe.services.PurchaseCommunicationService;
import com.example.adsportalbe.specifications.RoomPromotionSpecification;
import com.example.adsportalbe.utils.Utils;
import com.mk3.chatapp.dtos.responses.RoomPromotionEventDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.ChatRoomService;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mk3.chatapp.services.RoomPromotionPort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Room-level counterpart of {@link PromotedMessageServiceImpl}: same Stripe
 * manual-capture hold, same approve/deny/cancel money matrix, same WS and
 * notification fan-out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomPromotionServiceImpl implements RoomPromotionService {

    private static final long ROOM_PROMOTION_COST_CENTS = 250L;
    private static final double ROOM_PROMOTION_COST = ROOM_PROMOTION_COST_CENTS / 100.0;
    private static final List<RoomPromotionStatus> ACTIVE_STATUSES =
            List.of(RoomPromotionStatus.PENDING, RoomPromotionStatus.APPROVED);

    private final RoomPromotionRepository roomPromotionRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final PaymentService paymentService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final PurchaseCommunicationService purchaseCommunicationService;
    private final ChatRoomService chatRoomService;

    private static void requireStatus(RoomPromotion promotion, RoomPromotionStatus expected, String action) {
        if (promotion.getStatus() != expected) {
            throw new IllegalStateException("Only promotions with " + expected + " status can be " + action
                    + ". Current status: " + promotion.getStatus());
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }
    }

    private static double sumAmounts(List<RoomPromotion> promotions, RoomPromotionStatus status) {
        return promotions.stream()
                .filter(promotion -> promotion.getStatus() == status)
                .mapToDouble(promotion -> promotion.getAmount() != null ? promotion.getAmount() : 0)
                .sum();
    }

    private static double sumAmounts(List<RoomPromotion> promotions) {
        return promotions.stream()
                .mapToDouble(promotion -> promotion.getAmount() != null ? promotion.getAmount() : 0)
                .sum();
    }

    // Archiving refunds an APPROVED promotion only if the approval (= the charge)
    // happened inside this window; see RoomPromotionPort.ARCHIVE_REFUND_WINDOW_HOURS.
    private static final Duration ARCHIVE_REFUND_WINDOW =
            Duration.ofHours(RoomPromotionPort.ARCHIVE_REFUND_WINDOW_HOURS);

    private static boolean isWithinArchiveRefundWindow(RoomPromotion promotion, Instant now) {
        Instant approvedAt = promotion.getApprovedAt();
        return approvedAt != null && !approvedAt.isBefore(now.minus(ARCHIVE_REFUND_WINDOW));
    }

    @Override
    @Transactional(rollbackFor = StripeException.class)
    public RoomPromotionDetailDto promoteRoom(PromoteRoomRequestDto request, User user) throws StripeException {
        // Only claimed accounts may promote rooms (mirror promoteMessage)
        if (user == null || !user.isClaimed()) {
            throw new IllegalStateException("Account must be claimed to promote a room");
        }
        // Staff members are excluded from the paid funnel (admins would approve their own purchases)
        if (user.getRole() != null && user.getRole().isStaffMember()) {
            throw new IllegalStateException("Staff members cannot promote rooms");
        }
        if (request.chatRoomId() == null) {
            throw new IllegalArgumentException("Chat room ID cannot be null");
        }

        ChatRoom chatRoom = chatRoomService.findById(request.chatRoomId());

        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            throw new IllegalArgumentException("Private chat rooms cannot be promoted");
        }
        if (chatRoom.isArchived()) {
            throw new IllegalArgumentException("An archived chat room cannot be promoted");
        }
        if (chatRoom.getRequiredAccessLevel() != null && chatRoom.getRequiredAccessLevel().isStaffMember()) {
            throw new IllegalArgumentException("Staff-only chat rooms cannot be promoted");
        }
        if (isSpecialRoom(chatRoom)) {
            throw new IllegalArgumentException("This chat room cannot be promoted");
        }
        // One PENDING promotion per (owner, room); re-promoting an approved room is the "bump"
        if (roomPromotionRepository.existsByOwner_IdAndChatRoom_IdAndStatus(
                user.getId(), chatRoom.getId(), RoomPromotionStatus.PENDING)) {
            throw new ConflictException("You already have a pending promotion for this chat room");
        }

        String paymentIntentId = paymentService.authorizePayment(user, request.paymentMethodId(),
                ROOM_PROMOTION_COST_CENTS, null);

        PaymentReceipt.PaymentReceiptBuilder receiptBuilder = PaymentReceipt.builder()
                .stripePaymentIntentId(paymentIntentId)
                .amountPaid(ROOM_PROMOTION_COST)
                .currency("USD")
                .status("AUTHORIZED")
                .provider("STRIPE")
                .purchaseType(PurchaseType.ROOM_PROMOTION);

        PaymentMethodDto paymentMethodDto = paymentService.getPaymentMethod(request.paymentMethodId());
        if (paymentMethodDto != null) {
            receiptBuilder.cardBrand(paymentMethodDto.getBrand());
            receiptBuilder.cardLast4(paymentMethodDto.getLast4());
            receiptBuilder.cardholderName(paymentMethodDto.getCardholderName());
        }

        RoomPromotion promotion = RoomPromotion.builder()
                .chatRoom(chatRoom)
                .owner(user)
                .chatRoomName(chatRoom.getName())
                .status(RoomPromotionStatus.PENDING)
                .amount(ROOM_PROMOTION_COST)
                .currency("USD")
                .submittedAt(Instant.now())
                .receipt(receiptBuilder.build())
                .build();

        RoomPromotion saved = roomPromotionRepository.save(promotion);
        broadcastRoomPromotionUpdate(saved);
        notifyOwner(saved, NotificationType.ROOM_PROMOTION_SUBMITTED,
                "Your room promotion was submitted",
                "Your room promotion #" + saved.getId() + " for " + saved.getChatRoomName()
                        + " has been submitted and is awaiting review.");
        return toDetailDto(saved);
    }

    private boolean isSpecialRoom(ChatRoom chatRoom) {
        String name = chatRoom.getName();
        return name != null && chatRoomService.getAllSpecialChatRoomNames().stream()
                .anyMatch(special -> special.equalsIgnoreCase(name));
    }

    @Override
    public Page<RoomPromotionDto> getUserPromotions(User user, RoomPromotionStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Order.desc("submittedAt")));
        Page<RoomPromotion> result = status != null
                ? roomPromotionRepository.findByOwner_IdAndStatus(user.getId(), status, pageRequest)
                : roomPromotionRepository.findByOwner_Id(user.getId(), pageRequest);
        return result.map(this::toDto);
    }

    @Override
    public RoomPromotionDetailDto getById(Long id, User user) {
        RoomPromotion promotion = findPromotion(id);

        // Access control: non-staff users can only view their own promotions
        if (!user.getRole().isStaffMember() && !promotion.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: You can only view your own room promotions");
        }
        return toDetailDto(promotion);
    }


    @Override
    @Transactional
    public RoomPromotionDetailDto requestCancelByUser(Long id, String reason, User user) {
        requireReason(reason);
        RoomPromotion promotion = findPromotionForUpdate(id);
        if (!promotion.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: You can only cancel your own room promotions");
        }
        requireStatus(promotion, RoomPromotionStatus.PENDING, "cancel-requested");
        if (promotion.isCancelRequested()) {
            throw new ConflictException("A cancellation request is already pending review for this promotion");
        }

        promotion.setCancelRequested(true);
        promotion.setCancelRequestReason(reason);
        promotion.setCancelRequestedAt(Instant.now());
        RoomPromotion saved = roomPromotionRepository.save(promotion);
        notifyOwner(saved, NotificationType.ROOM_PROMOTION_CANCEL_REQUESTED,
                "Your cancellation request was received",
                "Your cancellation request for room promotion #" + saved.getId() + " for "
                        + saved.getChatRoomName() + " has been received. The purchase is still pending review"
                        + " and has not been canceled. Your reason: " + reason);
        return toDetailDto(saved);
    }

    @Override
    public Page<RoomPromotionDto> searchPromotions(RoomPromotionSearchRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request cannot be null");
        }

        List<Sort.Order> sortOrders = Utils.jsonStringToSortOrder(request.sort());

        // Default: oldest first so admins review the longest-waiting promotions
        if (sortOrders.isEmpty()) {
            sortOrders.add(Sort.Order.asc("submittedAt"));
        }

        PageRequest pageRequest = PageRequest.of(request.page(), request.size(), Sort.by(sortOrders));
        Specification<RoomPromotion> specification = RoomPromotionSpecification.getSpecification(request);

        return roomPromotionRepository.findAll(specification, pageRequest).map(this::toDto);
    }

    @Override
    @Transactional(rollbackFor = StripeException.class)
    public RoomPromotionDetailDto approve(Long id) throws StripeException {
        RoomPromotion promotion = findPromotionForUpdate(id);
        requireStatus(promotion, RoomPromotionStatus.PENDING, "approved");

        PaymentReceipt receipt = promotion.getReceipt();
        if (receipt == null || receipt.getStripePaymentIntentId() == null) {
            throw new IllegalStateException("No payment receipt found for room promotion " + id);
        }
        try {
            paymentService.capturePayment(receipt.getStripePaymentIntentId());
            receipt.setStatus("CAPTURED");
            receipt.setPaidAt(Instant.now());
        } catch (StripeException e) {
            log.error("Failed to capture payment for room promotion {}: {}", id, e.getMessage());
            throw e;
        }

        promotion.setStatus(RoomPromotionStatus.APPROVED);
        promotion.setApprovedAt(Instant.now());
        RoomPromotion saved = roomPromotionRepository.save(promotion);
        broadcastRoomPromotionUpdate(saved);
        notifyOwner(saved, NotificationType.ROOM_PROMOTION_APPROVED,
                "Your room promotion was approved",
                "Your room promotion #" + saved.getId() + " for " + saved.getChatRoomName()
                        + " has been approved and the room is now listed in Promoted Rooms.");
        return toDetailDto(saved);
    }

    @Override
    @Transactional(rollbackFor = StripeException.class)
    public RoomPromotionDetailDto deny(Long id, String reason) throws StripeException {
        requireReason(reason);
        RoomPromotion promotion = findPromotionForUpdate(id);
        requireStatus(promotion, RoomPromotionStatus.PENDING, "denied");

        releaseHold(promotion);
        return resolve(promotion, RoomPromotionStatus.DENIED, null, reason);
    }

    @Override
    @Transactional(rollbackFor = StripeException.class)
    public RoomPromotionDetailDto cancelByAdmin(Long id, String reason) throws StripeException {
        requireReason(reason);
        RoomPromotion promotion = findPromotionForUpdate(id);
        // PENDING: hold released; APPROVED: promotion stopped, payment kept
        if (promotion.getStatus() == RoomPromotionStatus.PENDING) {
            releaseHold(promotion);
        } else if (promotion.getStatus() != RoomPromotionStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only PENDING or APPROVED promotions can be canceled. Current status: " + promotion.getStatus());
        }
        return resolve(promotion, RoomPromotionStatus.CANCELED, CanceledBy.ADMIN, reason);
    }

    @Override
    public BanRoomPromotionsSummaryDto getBanPromotionsSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        List<RoomPromotion> promotions = roomPromotionRepository.findByOwner_Id(userId);
        Map<RoomPromotionStatus, Long> counts = promotions.stream()
                .collect(Collectors.groupingBy(RoomPromotion::getStatus, Collectors.counting()));

        double pendingReleaseTotal = sumAmounts(promotions, RoomPromotionStatus.PENDING);
        double approvedCapturedTotal = sumAmounts(promotions, RoomPromotionStatus.APPROVED);
        String currency = promotions.stream()
                .map(RoomPromotion::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("USD");

        return new BanRoomPromotionsSummaryDto(
                promotions.size(),
                counts.getOrDefault(RoomPromotionStatus.PENDING, 0L),
                counts.getOrDefault(RoomPromotionStatus.APPROVED, 0L),
                counts.getOrDefault(RoomPromotionStatus.DENIED, 0L),
                counts.getOrDefault(RoomPromotionStatus.CANCELED, 0L),
                pendingReleaseTotal,
                approvedCapturedTotal,
                currency);
    }

    @Override
    @Transactional
    public PromotionCancelOutcome cancelPromotionsForBannedUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Only PENDING holds are released on a permanent ban. APPROVED (captured)
        // promotions are NOT refunded and keep the room listed.
        List<RoomPromotion> pendingPromotions =
                roomPromotionRepository.findByOwnerIdAndStatusInForUpdate(userId, List.of(RoomPromotionStatus.PENDING));
        int released = 0;
        double totalReturned = 0;
        String currency = "USD";

        for (RoomPromotion promotion : pendingPromotions) {
            try {
                releaseHold(promotion);

                promotion.setStatus(RoomPromotionStatus.CANCELED);
                promotion.setCanceledBy(CanceledBy.SYSTEM_BAN);
                promotion.setResolvedAt(Instant.now());
                promotion.setReason("Owner permanently banned — pending promotion canceled and payment hold released.");
                roomPromotionRepository.save(promotion);
                broadcastRoomPromotionUpdate(promotion);
                notifyOwnerOfResolution(promotion, promotion.getStatus(), promotion.getCanceledBy(),
                        promotion.getReason());

                released++;
                totalReturned += promotion.getAmount() != null ? promotion.getAmount() : 0;
                if (promotion.getCurrency() != null && !promotion.getCurrency().isBlank()) {
                    currency = promotion.getCurrency();
                }
            } catch (Exception e) {
                // One failed cancellation must not abort the others; the ban itself
                // has already been committed by the chat module.
                log.error("Ban-cancel failed for room promotion {} (user {}): {}",
                        promotion.getId(), userId, e.getMessage());
            }
        }

        return new PromotionCancelOutcome(pendingPromotions.size(), released, 0, totalReturned, currency, 0);
    }

    @Override
    @Transactional
    public PromotionCancelOutcome cancelPromotionsForArchivedRoom(Long roomId) {
        if (roomId == null) {
            throw new IllegalArgumentException("Room ID cannot be null");
        }

        // Archiving is a platform decision, not a moderation one, so recent
        // purchases are made whole: PENDING holds are released and APPROVED
        // payments are refunded when the approval (= the charge) is within the
        // refund window. Older approved promotions already delivered their
        // value and are canceled without a refund.
        Instant now = Instant.now();
        List<RoomPromotion> active =
                roomPromotionRepository.findByChatRoomIdAndStatusInForUpdate(roomId, ACTIVE_STATUSES);
        int released = 0;
        int refunded = 0;
        int canceledWithoutRefund = 0;
        double totalReturned = 0;
        String currency = "USD";

        for (RoomPromotion promotion : active) {
            try {
                boolean wasPending = promotion.getStatus() == RoomPromotionStatus.PENDING;
                boolean refundable = !wasPending && isWithinArchiveRefundWindow(promotion, now);
                String reason;
                if (wasPending) {
                    releaseHold(promotion);
                    reason = "Chat room archived — pending promotion canceled and payment hold released.";
                } else if (refundable) {
                    refundCapturedPayment(promotion);
                    reason = "Chat room archived — promotion canceled and payment refund submitted (approved within the last "
                            + ARCHIVE_REFUND_WINDOW.toHours() + " hours).";
                } else {
                    reason = "Chat room archived — promotion canceled. It was approved more than "
                            + ARCHIVE_REFUND_WINDOW.toHours() + " hours ago, so the payment is not refunded.";
                }
                resolve(promotion, RoomPromotionStatus.CANCELED, CanceledBy.ADMIN, reason);
                if (wasPending) {
                    released++;
                } else if (refundable) {
                    refunded++;
                } else {
                    canceledWithoutRefund++;
                }
                if (wasPending || refundable) {
                    totalReturned += promotion.getAmount() != null ? promotion.getAmount() : 0;
                }
                if (promotion.getCurrency() != null && !promotion.getCurrency().isBlank()) {
                    currency = promotion.getCurrency();
                }
            } catch (Exception e) {
                // One failed cancellation must not abort the others; the room
                // has already been archived by the chat module.
                log.error("Archive-cancel failed for room promotion {} (room {}): {}",
                        promotion.getId(), roomId, e.getMessage());
            }
        }

        return new PromotionCancelOutcome(active.size(), released, refunded, totalReturned, currency,
                canceledWithoutRefund);
    }

    @Override
    public RoomPromotionsSummary getRoomPromotionsSummary(Long roomId) {
        if (roomId == null) {
            throw new IllegalArgumentException("Room ID cannot be null");
        }

        Instant now = Instant.now();
        List<RoomPromotion> active =
                roomPromotionRepository.findByChatRoom_IdAndStatusIn(roomId, ACTIVE_STATUSES);
        List<RoomPromotion> pending = active.stream()
                .filter(p -> p.getStatus() == RoomPromotionStatus.PENDING)
                .toList();
        List<RoomPromotion> refundable = active.stream()
                .filter(p -> p.getStatus() == RoomPromotionStatus.APPROVED && isWithinArchiveRefundWindow(p, now))
                .toList();
        List<RoomPromotion> nonRefundable = active.stream()
                .filter(p -> p.getStatus() == RoomPromotionStatus.APPROVED && !isWithinArchiveRefundWindow(p, now))
                .toList();
        String currency = active.stream()
                .map(RoomPromotion::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("USD");

        return new RoomPromotionsSummary(
                pending.size(),
                refundable.size() + nonRefundable.size(),
                refundable.size(),
                nonRefundable.size(),
                sumAmounts(pending),
                sumAmounts(refundable),
                sumAmounts(nonRefundable),
                currency);
    }

    @Override
    public Page<PromotedRoomRowDto> getPromotedRooms(int page, int size) {
        // Unsorted on purpose: the ordering (max(approvedAt) desc) is part of the query
        return roomPromotionRepository.findPromotedRooms(PageRequest.of(page, size));
    }

    @Override
    public PromotedRevenueSummaryDto getRevenueSummary() {
        // Mirrors PromotedMessageServiceImpl.getRevenueSummary for ROOM_PROMOTION receipts
        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();

        Instant todayStart = today.atStartOfDay(zoneId).toInstant();
        Instant todayEnd = Instant.now();
        Instant yesterdayStart = today.minusDays(1).atStartOfDay(zoneId).toInstant();
        Instant yesterdayEnd = todayStart.minusSeconds(1);

        Double todayRevenue = paymentReceiptRepository.sumCapturedAmountByPaidAtBetweenAndType(
                todayStart, todayEnd, PurchaseType.ROOM_PROMOTION);
        Double yesterdayRevenue = paymentReceiptRepository.sumCapturedAmountByPaidAtBetweenAndType(
                yesterdayStart, yesterdayEnd, PurchaseType.ROOM_PROMOTION);
        Double totalRevenue = paymentReceiptRepository.sumCapturedAmountByType(PurchaseType.ROOM_PROMOTION);

        return new PromotedRevenueSummaryDto(
                todayRevenue != null ? todayRevenue : 0.0,
                yesterdayRevenue != null ? yesterdayRevenue : 0.0,
                totalRevenue != null ? totalRevenue : 0.0,
                roomPromotionRepository.countByStatus(RoomPromotionStatus.PENDING),
                roomPromotionRepository.sumAmountByStatus(RoomPromotionStatus.PENDING),
                roomPromotionRepository.countByStatus(RoomPromotionStatus.APPROVED),
                "USD");
    }

    @Override
    public PromotedRevenueDailyResponseDto getDailyRevenue(LocalDate fromDate) {
        LocalDate effectiveFromDate = fromDate != null ? fromDate : LocalDate.now().minusMonths(3);
        ZoneId zoneId = ZoneId.systemDefault();
        Instant fromInstant = effectiveFromDate.atStartOfDay(zoneId).toInstant();
        Instant toInstant = LocalDate.now().plusDays(1).atStartOfDay(zoneId).toInstant();

        List<Object[]> rows = paymentReceiptRepository.findDailyCapturedRevenueForDateRangeByType(
                fromInstant, toInstant, PurchaseType.ROOM_PROMOTION);

        List<PromotedRevenueDailyResponseDto.DailyRevenue> dailyRevenue = rows.stream()
                .map(row -> new PromotedRevenueDailyResponseDto.DailyRevenue(
                        toLocalDate(row[0]),
                        row[1] != null ? ((Number) row[1]).doubleValue() : 0.0))
                .sorted(Comparator.comparing(PromotedRevenueDailyResponseDto.DailyRevenue::date))
                .toList();

        double totalRevenue = dailyRevenue.stream()
                .mapToDouble(PromotedRevenueDailyResponseDto.DailyRevenue::revenue)
                .sum();

        return new PromotedRevenueDailyResponseDto(dailyRevenue, totalRevenue);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return (LocalDate) value;
    }

    private RoomPromotion findPromotion(Long id) {
        return roomPromotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room promotion not found with id: " + id));
    }

    private RoomPromotion findPromotionForUpdate(Long id) {
        return roomPromotionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Room promotion not found with id: " + id));
    }

    private void refundCapturedPayment(RoomPromotion promotion) throws StripeException {
        PaymentReceipt receipt = promotion.getReceipt();
        if (receipt != null && receipt.getStripePaymentIntentId() != null
                && !receipt.getStripePaymentIntentId().isBlank()) {
            paymentService.refundPayment(receipt.getStripePaymentIntentId());
            receipt.setStatus("REFUNDED");
        } else {
            throw new IllegalStateException("No payment receipt found for room promotion " + promotion.getId());
        }
    }

    private void releaseHold(RoomPromotion promotion) throws StripeException {
        PaymentReceipt receipt = promotion.getReceipt();
        if (receipt != null && receipt.getStripePaymentIntentId() != null
                && !receipt.getStripePaymentIntentId().isBlank()) {
            paymentService.cancelPaymentAuthorization(receipt.getStripePaymentIntentId());
            receipt.setStatus("CANCELLED");
        } else {
            throw new IllegalStateException("No payment receipt found for room promotion " + promotion.getId());
        }
    }

    private RoomPromotionDetailDto resolve(RoomPromotion promotion, RoomPromotionStatus status,
                                          CanceledBy canceledBy, String reason) {
        promotion.setStatus(status);
        promotion.setCanceledBy(canceledBy);
        if (reason != null) {
            promotion.setReason(reason);
        }
        promotion.setResolvedAt(Instant.now());
        RoomPromotion saved = roomPromotionRepository.save(promotion);
        broadcastRoomPromotionUpdate(saved);
        notifyOwnerOfResolution(saved, status, canceledBy, reason);
        return toDetailDto(saved);
    }

    private void notifyOwnerOfResolution(RoomPromotion promotion, RoomPromotionStatus status,
                                         CanceledBy canceledBy, String reason) {
        if (status == RoomPromotionStatus.DENIED) {
            notifyOwner(promotion, NotificationType.ROOM_PROMOTION_DENIED,
                    "Your room promotion was denied",
                    "Your room promotion #" + promotion.getId() + " for " + promotion.getChatRoomName()
                            + " was denied. Reason: " + reason);
        } else if (status == RoomPromotionStatus.CANCELED) {
            String actor = canceledBy == CanceledBy.USER ? "at your request"
                    : canceledBy == CanceledBy.SYSTEM_BAN ? "automatically because of an account ban" : "by staff";
            notifyOwner(promotion, NotificationType.ROOM_PROMOTION_CANCELED,
                    "Your room promotion was canceled",
                    "Your room promotion #" + promotion.getId() + " for " + promotion.getChatRoomName()
                            + " was canceled " + actor + "."
                            + (reason == null || reason.isBlank() ? "" : " Reason: " + reason));
        }
    }

    private void notifyOwner(RoomPromotion promotion, NotificationType type, String title, String body) {
        purchaseCommunicationService.notifyOwner(promotion.getOwner(), PurchaseType.ROOM_PROMOTION,
                promotion.getId(), type, title, body, promotion.getReceipt());
    }

    private void broadcastRoomPromotionUpdate(RoomPromotion promotion) {
        try {
            var event = new RoomPromotionEventDTO(
                    promotion.getChatRoom().getId(),
                    promotion.getChatRoomName(),
                    promotion.getId(),
                    promotion.getStatus().name(),
                    promotion.getOwner().getId(),
                    promotion.getApprovedAt());
            var webSocketMessage = WebSocketMessage.builder()
                    .type(WebSocketMessageType.ROOM_PROMOTION_UPDATE)
                    .chatRoomName(promotion.getChatRoomName())
                    .data(event)
                    .build();
            // The promoted list is global, so every connected client refetches it
            webSocketBroadcastService.broadcastToPublicChat(webSocketMessage);
            // Also notify the owner directly so their portal pages update
            // (duplicate delivery is harmless — client handling is idempotent).
            webSocketBroadcastService.broadcastToUser(promotion.getOwner().getId(), webSocketMessage);
        } catch (Exception e) {
            // A broadcast failure must never fail the transition itself
            log.error("Failed to broadcast room promotion update for promotion {}: {}",
                    promotion.getId(), e.getMessage());
        }
    }

    private RoomPromotionDto toDto(RoomPromotion promotion) {
        return new RoomPromotionDto(
                promotion.getId(),
                promotion.getChatRoom().getId(),
                promotion.getChatRoomName(),
                promotion.getStatus(),
                promotion.getAmount(),
                promotion.getCurrency(),
                promotion.getSubmittedAt(),
                promotion.getApprovedAt(),
                promotion.getOwner().getEmail(),
                promotion.getOwner().getId(),
                promotion.isCancelRequested());
    }

    private RoomPromotionDetailDto toDetailDto(RoomPromotion promotion) {
        PaymentReceipt receipt = promotion.getReceipt();
        ChatRoom chatRoom = promotion.getChatRoom();
        return new RoomPromotionDetailDto(
                promotion.getId(),
                chatRoom.getId(),
                promotion.getChatRoomName(),
                chatRoom.isArchived(),
                promotion.getStatus(),
                promotion.getCanceledBy(),
                promotion.getReason(),
                promotion.getAmount(),
                promotion.getCurrency(),
                promotion.getSubmittedAt(),
                promotion.getApprovedAt(),
                promotion.getResolvedAt(),
                promotion.getOwner().getEmail(),
                promotion.getOwner().getId(),
                receipt != null ? receipt.getCardBrand() : null,
                receipt != null ? receipt.getCardLast4() : null,
                receipt != null ? receipt.getStatus() : null,
                promotion.isCancelRequested(),
                promotion.getCancelRequestReason(),
                promotion.getCancelRequestedAt());
    }
}
