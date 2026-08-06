package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.dto.payment.PaymentMethodDto;
import com.example.adsportalbe.dto.promotion.*;
import com.example.adsportalbe.enums.CanceledBy;
import com.example.adsportalbe.enums.PromotedMessageStatus;
import com.example.adsportalbe.enums.PurchaseType;
import com.example.adsportalbe.exceptions.ConflictException;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import com.example.adsportalbe.models.promotion.PromotedMessage;
import com.example.adsportalbe.repositories.PaymentReceiptRepository;
import com.example.adsportalbe.repositories.PromotedMessageRepository;
import com.example.adsportalbe.services.PaymentService;
import com.example.adsportalbe.services.PromotedMessageService;
import com.example.adsportalbe.specifications.PromotedMessageSpecification;
import com.example.adsportalbe.utils.Utils;
import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.responses.PromotedMessageEventDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.mappers.AttachmentMapper;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.services.NotificationService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotedMessageServiceImpl implements PromotedMessageService {

    private static final long PROMOTION_COST_CENTS = 50L;
    private static final double PROMOTION_COST = PROMOTION_COST_CENTS / 100.0;
    private static final List<PromotedMessageStatus> ACTIVE_STATUSES =
            List.of(PromotedMessageStatus.PENDING, PromotedMessageStatus.APPROVED);
    private static final int SNIPPET_LENGTH = 80;

    private final PromotedMessageRepository promotedMessageRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final MessageRepository messageRepository;
    private final PaymentService paymentService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final AttachmentMapper attachmentMapper;
    private final NotificationService notificationService;

    private static void requireStatus(PromotedMessage promotion, PromotedMessageStatus expected, String action) {
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

    private static double sumAmounts(List<PromotedMessage> promotions, PromotedMessageStatus status) {
        return promotions.stream()
                .filter(promotion -> promotion.getStatus() == status)
                .mapToDouble(promotion -> promotion.getAmount() != null ? promotion.getAmount() : 0)
                .sum();
    }

    private static String snippet(String content) {
        if (content == null || content.length() <= SNIPPET_LENGTH) {
            return content;
        }
        return content.substring(0, SNIPPET_LENGTH) + "…";
    }

    @Override
    @Transactional
    public PromotedMessageDetailDto promoteMessage(PromoteMessageRequestDto request, User user)
            throws StripeException {
        // Only claimed accounts may promote messages (mirror createAd)
        if (user == null || !user.isClaimed()) {
            throw new IllegalStateException("Account must be claimed to promote a message");
        }
        if (request.messageId() == null) {
            throw new IllegalArgumentException("Message ID cannot be null");
        }

        Message message = messageRepository.findById(request.messageId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Message not found with id: " + request.messageId()));

        if (Boolean.TRUE.equals(message.getDeleted()) || Boolean.TRUE.equals(message.getQuarantined())) {
            throw new IllegalArgumentException("A removed message cannot be promoted");
        }
        if (!message.getSender().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: You can only promote your own messages");
        }
        if (message.getChatRoom().getType() == ChatRoomType.PRIVATE) {
            throw new IllegalArgumentException("Messages in private chat rooms cannot be promoted");
        }
        if (promotedMessageRepository.existsByMessage_IdAndStatusIn(message.getId(), ACTIVE_STATUSES)) {
            throw new ConflictException("This message already has an active promotion");
        }

        String paymentIntentId = paymentService.authorizePayment(user, request.paymentMethodId(),
                PROMOTION_COST_CENTS, null);

        PaymentReceipt.PaymentReceiptBuilder receiptBuilder = PaymentReceipt.builder()
                .stripePaymentIntentId(paymentIntentId)
                .amountPaid(PROMOTION_COST)
                .currency("USD")
                .status("AUTHORIZED")
                .provider("STRIPE")
                .purchaseType(PurchaseType.PROMOTED_MESSAGE);

        PaymentMethodDto paymentMethodDto = paymentService.getPaymentMethod(request.paymentMethodId());
        if (paymentMethodDto != null) {
            receiptBuilder.cardBrand(paymentMethodDto.getBrand());
            receiptBuilder.cardLast4(paymentMethodDto.getLast4());
            receiptBuilder.cardholderName(paymentMethodDto.getCardholderName());
        }

        PromotedMessage promotion = PromotedMessage.builder()
                .message(message)
                .owner(user)
                .chatRoomId(message.getChatRoom().getId())
                .chatRoomName(message.getChatRoom().getName())
                .status(PromotedMessageStatus.PENDING)
                .amount(PROMOTION_COST)
                .currency("USD")
                .submittedAt(Instant.now())
                .receipt(receiptBuilder.build())
                .build();

        PromotedMessage saved = promotedMessageRepository.save(promotion);
        broadcastPromotionUpdate(saved);
        return toDetailDto(saved);
    }

    @Override
    public Page<PromotedMessageDto> getUserPromotions(User user, PromotedMessageStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Order.desc("submittedAt")));
        Page<PromotedMessage> result = status != null
                ? promotedMessageRepository.findByOwner_IdAndStatus(user.getId(), status, pageRequest)
                : promotedMessageRepository.findByOwner_Id(user.getId(), pageRequest);
        return result.map(this::toDto);
    }

    @Override
    public PromotedMessageDetailDto getById(Long id, User user) {
        PromotedMessage promotion = findPromotion(id);

        // Access control: non-staff users can only view their own promotions
        if (!user.getRole().isStaffMember() && !promotion.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: You can only view your own promoted messages");
        }
        return toDetailDto(promotion);
    }

    @Override
    @Transactional
    public PromotedMessageDetailDto cancelByUser(Long id, User user) throws StripeException {
        PromotedMessage promotion = findPromotion(id);
        if (!promotion.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: You can only cancel your own promoted messages");
        }

        // APPROVED self-cancel = no refund; PENDING must go through request-cancel
        if (promotion.getStatus() == PromotedMessageStatus.PENDING) {
            throw new IllegalStateException(
                    "Pending promotions cannot be canceled directly — submit a cancellation request instead.");
        }
        if (promotion.getStatus() != PromotedMessageStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only APPROVED promotions can be canceled. Current status: " + promotion.getStatus());
        }

        return resolve(promotion, PromotedMessageStatus.CANCELED, CanceledBy.USER, null);
    }

    @Override
    @Transactional
    public PromotedMessageDetailDto requestCancelByUser(Long id, String reason, User user) {
        requireReason(reason);
        PromotedMessage promotion = findPromotion(id);
        if (!promotion.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: You can only cancel your own promoted messages");
        }
        requireStatus(promotion, PromotedMessageStatus.PENDING, "cancel-requested");
        if (promotion.isCancelRequested()) {
            throw new ConflictException("A cancellation request is already pending review for this promotion");
        }

        promotion.setCancelRequested(true);
        promotion.setCancelRequestReason(reason);
        promotion.setCancelRequestedAt(Instant.now());
        return toDetailDto(promotedMessageRepository.save(promotion));
    }

    @Override
    public Page<PromotedMessageDto> searchPromotions(PromotedMessageSearchRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request cannot be null");
        }

        List<Sort.Order> sortOrders = Utils.jsonStringToSortOrder(request.sort());

        // Default: oldest first so admins review the longest-waiting promotions
        if (sortOrders.isEmpty()) {
            sortOrders.add(Sort.Order.asc("submittedAt"));
        }

        PageRequest pageRequest = PageRequest.of(request.page(), request.size(), Sort.by(sortOrders));
        Specification<PromotedMessage> specification = PromotedMessageSpecification.getSpecification(request);

        return promotedMessageRepository.findAll(specification, pageRequest).map(this::toDto);
    }

    @Override
    @Transactional
    public PromotedMessageDetailDto approve(Long id) throws StripeException {
        PromotedMessage promotion = findPromotion(id);
        requireStatus(promotion, PromotedMessageStatus.PENDING, "approved");

        PaymentReceipt receipt = promotion.getReceipt();
        if (receipt == null || receipt.getStripePaymentIntentId() == null) {
            throw new IllegalStateException("No payment receipt found for promotion " + id);
        }
        try {
            paymentService.capturePayment(receipt.getStripePaymentIntentId());
            receipt.setStatus("CAPTURED");
            receipt.setPaidAt(Instant.now());
        } catch (StripeException e) {
            log.error("Failed to capture payment for promotion {}: {}", id, e.getMessage());
            throw e;
        }

        promotion.setStatus(PromotedMessageStatus.APPROVED);
        promotion.setApprovedAt(Instant.now());
        PromotedMessage saved = promotedMessageRepository.save(promotion);
        broadcastPromotionUpdate(saved);
        notificationService.createAndSend(saved.getOwner(), NotificationType.PROMOTION_APPROVED,
                "Your promoted message was approved",
                "Your promoted message in " + saved.getChatRoomName() + " has been approved and is now live.",
                null, "PROMOTED_MESSAGE", saved.getId());
        return toDetailDto(saved);
    }

    @Override
    @Transactional
    public PromotedMessageDetailDto deny(Long id, String reason) throws StripeException {
        requireReason(reason);
        PromotedMessage promotion = findPromotion(id);
        requireStatus(promotion, PromotedMessageStatus.PENDING, "denied");

        releaseHold(promotion);
        return resolve(promotion, PromotedMessageStatus.DENIED, null, reason);
    }

    @Override
    @Transactional
    public PromotedMessageDetailDto cancelByAdmin(Long id, String reason) throws StripeException {
        requireReason(reason);
        // PENDING: hold released; APPROVED: promotion stopped, payment kept
        return cancelActive(findPromotion(id), CanceledBy.ADMIN, reason);
    }

    @Override
    public BanPromotionsSummaryDto getBanPromotionsSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        List<PromotedMessage> promotions = promotedMessageRepository.findByOwner_Id(userId);
        Map<PromotedMessageStatus, Long> counts = promotions.stream()
                .collect(Collectors.groupingBy(PromotedMessage::getStatus, Collectors.counting()));

        double pendingReleaseTotal = sumAmounts(promotions, PromotedMessageStatus.PENDING);
        double approvedRefundTotal = sumAmounts(promotions, PromotedMessageStatus.APPROVED);
        String currency = promotions.stream()
                .map(PromotedMessage::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("USD");

        return new BanPromotionsSummaryDto(
                promotions.size(),
                counts.getOrDefault(PromotedMessageStatus.PENDING, 0L),
                counts.getOrDefault(PromotedMessageStatus.APPROVED, 0L),
                counts.getOrDefault(PromotedMessageStatus.DENIED, 0L),
                counts.getOrDefault(PromotedMessageStatus.CANCELED, 0L),
                pendingReleaseTotal,
                approvedRefundTotal,
                currency);
    }

    @Override
    public PromotedRevenueSummaryDto getRevenueSummary() {
        // Same boundary handling as AdServiceImpl.getDailyRevenueStats
        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();

        Instant todayStart = today.atStartOfDay(zoneId).toInstant();
        Instant todayEnd = Instant.now();
        Instant yesterdayStart = today.minusDays(1).atStartOfDay(zoneId).toInstant();
        Instant yesterdayEnd = todayStart.minusSeconds(1);

        Double todayRevenue = paymentReceiptRepository.sumCapturedAmountByPaidAtBetweenAndType(
                todayStart, todayEnd, PurchaseType.PROMOTED_MESSAGE);
        Double yesterdayRevenue = paymentReceiptRepository.sumCapturedAmountByPaidAtBetweenAndType(
                yesterdayStart, yesterdayEnd, PurchaseType.PROMOTED_MESSAGE);
        Double totalRevenue = paymentReceiptRepository.sumCapturedAmountByType(PurchaseType.PROMOTED_MESSAGE);

        return new PromotedRevenueSummaryDto(
                todayRevenue != null ? todayRevenue : 0.0,
                yesterdayRevenue != null ? yesterdayRevenue : 0.0,
                totalRevenue != null ? totalRevenue : 0.0,
                promotedMessageRepository.countByStatus(PromotedMessageStatus.PENDING),
                promotedMessageRepository.sumAmountByStatus(PromotedMessageStatus.PENDING),
                promotedMessageRepository.countByStatus(PromotedMessageStatus.APPROVED),
                "USD");
    }

    @Override
    public PromotedRevenueDailyResponseDto getDailyRevenue(LocalDate fromDate) {
        LocalDate effectiveFromDate = fromDate != null ? fromDate : LocalDate.now().minusMonths(3);
        ZoneId zoneId = ZoneId.systemDefault();
        Instant fromInstant = effectiveFromDate.atStartOfDay(zoneId).toInstant();
        Instant toInstant = LocalDate.now().plusDays(1).atStartOfDay(zoneId).toInstant();

        List<Object[]> rows = paymentReceiptRepository.findDailyCapturedRevenueForDateRangeByType(
                fromInstant, toInstant, PurchaseType.PROMOTED_MESSAGE);

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

    @Override
    public PromotionSpendSummaryDto getSpendSummary(User user) {
        Long ownerId = user.getId();
        return new PromotionSpendSummaryDto(
                promotedMessageRepository.countByOwner_IdAndStatus(ownerId, PromotedMessageStatus.PENDING),
                promotedMessageRepository.sumAmountByOwnerIdAndStatus(ownerId, PromotedMessageStatus.PENDING),
                promotedMessageRepository.countByOwner_IdAndStatus(ownerId, PromotedMessageStatus.APPROVED),
                promotedMessageRepository.sumCapturedSpendByOwnerId(ownerId),
                "USD");
    }

    // DATE() comes back as java.sql.Date or LocalDate depending on the dialect
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return (LocalDate) value;
    }

    @Override
    @Transactional
    public PromotionCancelOutcome cancelPromotionsForBannedUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Only PENDING holds are released on a permanent ban. APPROVED (captured)
        // promotions are NOT refunded and keep running; they are only stopped —
        // without a refund — if the ban's message deletion removes their message.
        List<PromotedMessage> pendingPromotions =
                promotedMessageRepository.findByOwner_IdAndStatusIn(userId, List.of(PromotedMessageStatus.PENDING));
        int released = 0;
        double totalReturned = 0;
        String currency = "USD";

        for (PromotedMessage promotion : pendingPromotions) {
            try {
                releaseHold(promotion);

                promotion.setStatus(PromotedMessageStatus.CANCELED);
                promotion.setCanceledBy(CanceledBy.SYSTEM_BAN);
                promotion.setResolvedAt(Instant.now());
                promotion.setReason("Owner permanently banned — pending promotion canceled and payment hold released.");
                promotedMessageRepository.save(promotion);
                broadcastPromotionUpdate(promotion);

                released++;
                totalReturned += promotion.getAmount() != null ? promotion.getAmount() : 0;
                if (promotion.getCurrency() != null && !promotion.getCurrency().isBlank()) {
                    currency = promotion.getCurrency();
                }
            } catch (Exception e) {
                // One failed cancellation must not abort the others; the ban itself
                // has already been committed by the chat module.
                log.error("Ban-cancel failed for promotion {} (user {}): {}",
                        promotion.getId(), userId, e.getMessage());
            }
        }

        return new PromotionCancelOutcome(pendingPromotions.size(), released, 0, totalReturned, currency);
    }

    @Override
    @Transactional
    public void cancelForMessageRemoval(Long messageId, boolean removedByStaff) throws StripeException {
        if (messageId == null) {
            return;
        }
        List<PromotedMessage> active =
                promotedMessageRepository.findByMessage_IdInAndStatusIn(List.of(messageId), ACTIVE_STATUSES);
        for (PromotedMessage promotion : active) {
            cancelActive(promotion,
                    removedByStaff ? CanceledBy.ADMIN : CanceledBy.USER,
                    removedByStaff ? "Message removed by staff" : "Message deleted by its author");
        }
    }

    @Override
    @Transactional
    public PromotionCancelOutcome cancelPromotionsForDeletedUserMessages(Long userId, Instant cutoff) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        Instant effectiveCutoff = cutoff != null ? cutoff : Instant.EPOCH;

        List<PromotedMessage> affected =
                promotedMessageRepository.findByOwner_IdAndStatusIn(userId, ACTIVE_STATUSES).stream()
                        .filter(promotion -> promotion.getMessage().getCreatedAt() != null
                                && promotion.getMessage().getCreatedAt().isAfter(effectiveCutoff))
                        .toList();
        int released = 0;
        int canceled = 0;
        double totalReturned = 0;
        String currency = "USD";

        for (PromotedMessage promotion : affected) {
            try {
                boolean wasPending = promotion.getStatus() == PromotedMessageStatus.PENDING;
                cancelActive(promotion, CanceledBy.SYSTEM_BAN,
                        "Message deleted due to a ban — pending holds released; approved promotions are not refunded.");
                if (wasPending) {
                    released++;
                    totalReturned += promotion.getAmount() != null ? promotion.getAmount() : 0;
                } else {
                    canceled++;
                }
                if (promotion.getCurrency() != null && !promotion.getCurrency().isBlank()) {
                    currency = promotion.getCurrency();
                }
            } catch (Exception e) {
                // One failed cancellation must not abort the others; the ban and
                // message deletion have already happened.
                log.error("Ban message-deletion cancel failed for promotion {} (user {}): {}",
                        promotion.getId(), userId, e.getMessage());
            }
        }

        return new PromotionCancelOutcome(affected.size(), released, canceled, totalReturned, currency);
    }

    @Override
    public Map<Long, ActivePromotion> getActivePromotions(Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        return promotedMessageRepository.findByMessage_IdInAndStatusIn(messageIds, ACTIVE_STATUSES).stream()
                .collect(Collectors.toMap(
                        promotion -> promotion.getMessage().getId(),
                        promotion -> new ActivePromotion(promotion.getId(), promotion.getStatus()),
                        (first, second) -> first));
    }

    @Override
    public Page<Long> getApprovedPromotedMessageIds(Long roomId, int page, int size) {
        return promotedMessageRepository.findApprovedMessageIdsByRoom(roomId, PageRequest.of(page, size));
    }

    private PromotedMessage findPromotion(Long id) {
        return promotedMessageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promoted message not found with id: " + id));
    }

    /**
     * Moderation-cancel semantics shared by admin cancel, staff message removal
     * and ban-triggered message deletion: a PENDING hold is released; an
     * APPROVED (captured) payment is NOT refunded.
     */
    private PromotedMessageDetailDto cancelActive(PromotedMessage promotion, CanceledBy canceledBy, String reason)
            throws StripeException {
        if (promotion.getStatus() == PromotedMessageStatus.PENDING) {
            releaseHold(promotion);
        } else if (promotion.getStatus() != PromotedMessageStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only PENDING or APPROVED promotions can be canceled. Current status: " + promotion.getStatus());
        }
        return resolve(promotion, PromotedMessageStatus.CANCELED, canceledBy, reason);
    }

    private void releaseHold(PromotedMessage promotion) throws StripeException {
        PaymentReceipt receipt = promotion.getReceipt();
        if (receipt != null && receipt.getStripePaymentIntentId() != null) {
            paymentService.cancelPaymentAuthorization(receipt.getStripePaymentIntentId());
            receipt.setStatus("CANCELLED");
        } else {
            log.warn("No payment receipt found for promotion {}", promotion.getId());
        }
    }

    private PromotedMessageDetailDto resolve(PromotedMessage promotion, PromotedMessageStatus status,
                                             CanceledBy canceledBy, String reason) {
        promotion.setStatus(status);
        promotion.setCanceledBy(canceledBy);
        if (reason != null) {
            promotion.setReason(reason);
        }
        promotion.setResolvedAt(Instant.now());
        PromotedMessage saved = promotedMessageRepository.save(promotion);
        broadcastPromotionUpdate(saved);
        notifyOwnerOfResolution(saved, status, canceledBy, reason);
        return toDetailDto(saved);
    }

    /**
     * Persistent owner notification for staff-driven resolutions: DENIED, and
     * CANCELED only when staff initiated it. USER cancels are the owner's own
     * action and SYSTEM_BAN owners cannot access notifications.
     */
    private void notifyOwnerOfResolution(PromotedMessage promotion, PromotedMessageStatus status,
                                         CanceledBy canceledBy, String reason) {
        if (status == PromotedMessageStatus.DENIED) {
            notificationService.createAndSend(promotion.getOwner(), NotificationType.PROMOTION_DENIED,
                    "Your promoted message was denied",
                    "Your promoted message in " + promotion.getChatRoomName() + " was denied. Reason: " + reason,
                    null, "PROMOTED_MESSAGE", promotion.getId());
        } else if (status == PromotedMessageStatus.CANCELED && canceledBy == CanceledBy.ADMIN) {
            notificationService.createAndSend(promotion.getOwner(), NotificationType.PROMOTION_CANCELED,
                    "Your promoted message was canceled",
                    "Your promoted message in " + promotion.getChatRoomName() + " was canceled by staff. Reason: " + reason,
                    null, "PROMOTED_MESSAGE", promotion.getId());
        }
    }

    private void broadcastPromotionUpdate(PromotedMessage promotion) {
        try {
            var event = new PromotedMessageEventDTO(
                    promotion.getMessage().getId(),
                    promotion.getChatRoomId(),
                    promotion.getChatRoomName(),
                    promotion.getId(),
                    promotion.getStatus().name(),
                    promotion.getOwner().getId());
            var webSocketMessage = WebSocketMessage.builder()
                    .type(WebSocketMessageType.PROMOTED_MESSAGE_UPDATE)
                    .chatRoomName(promotion.getChatRoomName())
                    .data(event)
                    .build();
            webSocketBroadcastService.broadcastToChatRoom(promotion.getChatRoomName(), webSocketMessage);
            // Also notify the owner directly so their portal pages update even
            // when the room topic isn't subscribed (duplicate delivery is
            // harmless — the client-side handling is idempotent).
            webSocketBroadcastService.broadcastToUser(promotion.getOwner().getId(), webSocketMessage);
        } catch (Exception e) {
            // A broadcast failure must never fail the transition itself
            log.error("Failed to broadcast promotion update for promotion {}: {}",
                    promotion.getId(), e.getMessage());
        }
    }

    private PromotedMessageDto toDto(PromotedMessage promotion) {
        return new PromotedMessageDto(
                promotion.getId(),
                promotion.getMessage().getId(),
                snippet(promotion.getMessage().getContent()),
                promotion.getChatRoomId(),
                promotion.getChatRoomName(),
                promotion.getStatus(),
                promotion.getAmount(),
                promotion.getCurrency(),
                promotion.getSubmittedAt(),
                promotion.getOwner().getEmail(),
                promotion.getOwner().getId(),
                promotion.isCancelRequested());
    }

    private PromotedMessageDetailDto toDetailDto(PromotedMessage promotion) {
        PaymentReceipt receipt = promotion.getReceipt();
        Message message = promotion.getMessage();
        List<AttachmentDTO> attachments = message.getAttachments() == null ? List.of()
                : message.getAttachments().stream().map(attachmentMapper::toDto).toList();
        return new PromotedMessageDetailDto(
                promotion.getId(),
                message.getId(),
                message.getContent(),
                message.getSender().getApplicationUsername(),
                message.getCreatedAt(),
                Boolean.TRUE.equals(message.getDeleted()),
                attachments,
                promotion.getChatRoomId(),
                promotion.getChatRoomName(),
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
