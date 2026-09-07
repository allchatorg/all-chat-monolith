package com.example.adsportalbe.services;

import com.example.adsportalbe.enums.PurchaseType;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import com.example.adsportalbe.models.payment.PurchaseEmailOutbox;
import com.example.adsportalbe.repositories.PurchaseEmailOutboxRepository;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseCommunicationService {

    private final NotificationService notificationService;
    private final PurchaseEmailOutboxRepository outboxRepository;

    /**
     * Joins the purchase transaction so its notification and email intent either both commit or both roll back.
     * Existing purchase transitions must lock their domain row before calling this method. The event key is a
     * second line of defense against duplicate transition delivery, including updates for legacy email-less owners.
     */
    @Transactional
    public void notifyOwner(User owner, PurchaseType purchaseType, Long purchaseId, NotificationType type,
                            String title, String body, PaymentReceipt receipt) {
        Objects.requireNonNull(owner, "Purchase owner is required");
        Objects.requireNonNull(owner.getId(), "Persisted purchase owner is required");
        Objects.requireNonNull(purchaseType, "Purchase type is required");
        Objects.requireNonNull(purchaseId, "Persisted purchase is required");
        Objects.requireNonNull(type, "Notification type is required");

        String eventKey = purchaseType.name() + ":" + purchaseId + ":" + type.name();
        if (outboxRepository.existsByEventKey(eventKey)) {
            return;
        }

        String paymentSummary = paymentSummary(receipt, type);
        String completeBody = body == null ? "" : body.strip();
        if (StringUtils.hasText(paymentSummary)) {
            completeBody = completeBody.isEmpty() ? paymentSummary : completeBody + "\n\n" + paymentSummary;
        }
        String email = StringUtils.hasText(owner.getEmail()) ? owner.getEmail().strip() : null;
        Instant now = Instant.now();
        outboxRepository.save(PurchaseEmailOutbox.builder()
                .eventKey(eventKey)
                .recipientUserId(owner.getId())
                .recipientEmail(email)
                .title(title)
                .body(completeBody)
                .purchaseReference(purchaseReference(purchaseType, purchaseId))
                .detailsPath(detailsPath(purchaseType, purchaseId))
                .createdAt(now)
                .nextAttemptAt(email == null ? null : now)
                .lastFailureCode(email == null ? "MISSING_EMAIL" : null)
                .build());

        notificationService.createAndSend(owner, type, title, completeBody, null, purchaseType.name(), purchaseId);

        if (email == null) {
            log.warn("Purchase email blocked: userId={}, eventKey={}, reason=MISSING_EMAIL. Chat notification saved.",
                    owner.getId(), eventKey);
        }
    }

    private String purchaseReference(PurchaseType type, Long id) {
        return switch (type) {
            case AD -> "Advertisement #" + id;
            case PROMOTED_MESSAGE -> "Promoted message #" + id;
            case ROOM_PROMOTION -> "Room promotion #" + id;
        };
    }

    private String detailsPath(PurchaseType type, Long id) {
        return switch (type) {
            case AD -> "/portal/ads/" + id;
            case PROMOTED_MESSAGE -> "/portal/promoted-messages/" + id;
            case ROOM_PROMOTION -> "/portal/room-promotions/" + id;
        };
    }

    private String paymentSummary(PaymentReceipt receipt, NotificationType notificationType) {
        if (receipt == null || receipt.getStatus() == null) {
            return "";
        }
        String amount = formattedAmount(receipt);
        return switch (receipt.getStatus().toUpperCase(Locale.ROOT)) {
            case "AUTHORIZED" -> "Payment: A temporary authorization hold" + amount
                    + " has been placed on your payment method. You have not been charged.";
            case "CAPTURED" -> "Payment: Your payment" + amount + " has been charged."
                    + (isCanceledOrDenied(notificationType) ? " No refund has been issued." : "");
            case "CANCELLED", "CANCELED" -> "Payment: The payment authorization" + amount
                    + " has been canceled and the hold released. Your bank may take time to remove the pending hold.";
            case "REFUNDED" -> "Payment: A refund" + amount
                    + " has been submitted. Your bank may take time to process it and show it on your account.";
            default -> "";
        };
    }

    private boolean isCanceledOrDenied(NotificationType type) {
        return switch (type) {
            case AD_REJECTED, AD_CANCELED, PROMOTION_DENIED, PROMOTION_CANCELED,
                    ROOM_PROMOTION_DENIED, ROOM_PROMOTION_CANCELED -> true;
            default -> false;
        };
    }

    private String formattedAmount(PaymentReceipt receipt) {
        Double amount = receipt.getAmountPaid();
        if (amount == null || !Double.isFinite(amount) || amount < 0 || !StringUtils.hasText(receipt.getCurrency())) {
            return "";
        }
        try {
            Currency currency = Currency.getInstance(receipt.getCurrency().toUpperCase(Locale.ROOT));
            int scale = Math.max(currency.getDefaultFractionDigits(), 0);
            return " of " + currency.getCurrencyCode() + " "
                    + BigDecimal.valueOf(amount).setScale(scale, RoundingMode.HALF_UP).toPlainString();
        } catch (IllegalArgumentException ignored) {
            // An incomplete legacy receipt must not turn a purchase transition into an error or invent an amount.
            return "";
        }
    }
}
