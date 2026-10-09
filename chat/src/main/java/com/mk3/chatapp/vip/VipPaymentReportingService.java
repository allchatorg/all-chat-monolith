package com.mk3.chatapp.vip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.models.vip.VipReportingState;
import com.mk3.chatapp.models.vip.VipSubscriptionPayment;
import com.mk3.chatapp.repositories.VipReportingStateRepository;
import com.mk3.chatapp.repositories.VipSubscriptionPaymentRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.param.EventListParams;
import com.stripe.param.InvoiceLineItemCollectionListParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A separate reporting transaction never relies on entitlement-event deduplication. */
@Service
@RequiredArgsConstructor
public class VipPaymentReportingService {
    private static final Set<String> EVENTS = Set.of("invoice.paid", "charge.refunded", "charge.refund.updated",
            "refund.created", "refund.updated", "refund.failed");
    private static final long PAGE_SIZE = 25;
    private static final long OVERLAP_SECONDS = 300;
    private final VipConfiguration config;
    private final VipReportingStateRepository states;
    private final VipSubscriptionPaymentRepository payments;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public static boolean supportsEvent(String type) {
        return EVENTS.contains(type);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initialize() {
        // Atomic on-conflict insertion preserves the recovery checkpoint across restarts and instances.
        states.initialize(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void handleEvent(String type, JsonNode object) throws StripeException {
        if (!supportsEvent(type)) return;
        lockedState();
        process(type, object);
    }

    /** One bounded page per invocation; the stable window and cursor survive restarts. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void reconcilePage() throws StripeException {
        if (!config.hasApiKey()) return;
        VipReportingState state = lockedState();
        Instant now = Instant.now();
        // Stripe retains events for 30 days. Leave a minute for boundary/indexing uncertainty.
        Instant oldest = now.minus(30, ChronoUnit.DAYS).plusSeconds(60).truncatedTo(ChronoUnit.SECONDS);
        Instant availableThrough = now.minusSeconds(60).truncatedTo(ChronoUnit.SECONDS);
        Instant checkpoint = state.getLastSynchronizedAt();
        Instant pendingFrom = state.getWindowStart() == null ? checkpoint : state.getWindowStart();
        if (pendingFrom.isBefore(oldest)) {
            state.setIncomplete(true); // Later successful pages cannot repair a known lost interval.
            state.setWindowStart(oldest);
            state.setWindowEnd(null);
            state.setEventCursor(null);
        }
        if (state.getWindowEnd() == null) {
            Instant from = state.getWindowStart() != null ? state.getWindowStart()
                    : state.isSynchronizedOnce() ? checkpoint.minusSeconds(OVERLAP_SECONDS) : checkpoint;
            if (from.isBefore(oldest)) from = oldest;
            Instant end = from.plus(1, ChronoUnit.DAYS);
            if (end.isAfter(availableThrough)) end = availableThrough;
            if (!end.isAfter(from)) return;
            state.setWindowStart(from);
            state.setWindowEnd(end);
            state.setEventCursor(null);
        }
        var params = EventListParams.builder().setLimit(PAGE_SIZE).addAllType(EVENTS.stream().sorted().toList())
                .setCreated(EventListParams.Created.builder()
                        .setGte(state.getWindowStart().getEpochSecond())
                        .setLt(state.getWindowEnd().getEpochSecond()).build());
        if (state.getEventCursor() != null) params.setStartingAfter(state.getEventCursor());
        var page = Event.list(params.build(), config.requestOptions());
        for (Event event : page.getData()) {
            JsonNode object;
            try {
                // Reading only stable identifiers also handles events emitted under older API versions.
                object = objectMapper.readTree(event.getDataObjectDeserializer().getRawJson());
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read a subscription reporting event", e);
            }
            process(event.getType(), object);
        }
        if (Boolean.TRUE.equals(page.getHasMore())) {
            if (page.getData().isEmpty()) throw new IllegalStateException("Stripe returned an empty continuation page");
            state.setEventCursor(page.getData().get(page.getData().size() - 1).getId());
        } else {
            state.setLastSynchronizedAt(state.getWindowEnd());
            state.setSynchronizedOnce(true);
            state.setWindowStart(null);
            state.setWindowEnd(null);
            state.setEventCursor(null);
        }
        state.setSynchronizationFailed(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSynchronizationFailed() {
        lockedState().setSynchronizationFailed(true);
    }

    private void process(String type, JsonNode object) throws StripeException {
        String invoiceId;
        if ("invoice.paid".equals(type)) {
            invoiceId = object.path("id").asText(null);
        } else {
            String chargeId = "charge.refunded".equals(type) ? object.path("id").asText(null) : id(object.path("charge"));
            if (chargeId == null) return;
            // The event can describe an old/partial refund. Always read the current aggregate.
            invoiceId = Charge.retrieve(chargeId, config.requestOptions()).getInvoice();
        }
        if (invoiceId != null) recordInvoice(invoiceId);
    }

    private void recordInvoice(String invoiceId) throws StripeException {
        Invoice invoice = Invoice.retrieve(invoiceId, config.requestOptions());
        if (!Boolean.TRUE.equals(invoice.getPaid()) || !"paid".equals(invoice.getStatus())
                || Boolean.TRUE.equals(invoice.getPaidOutOfBand()) || !"usd".equals(invoice.getCurrency())
                || invoice.getAmountPaid() == null || invoice.getAmountPaid() <= 0
                || invoice.getSubscription() == null || invoice.getCustomer() == null
                || invoice.getStatusTransitions() == null || invoice.getStatusTransitions().getPaidAt() == null) return;
        Instant paidAt = Instant.ofEpochSecond(invoice.getStatusTransitions().getPaidAt());

        VipSubscriptionPayment payment = payments.findById(invoiceId).orElse(null);
        Long ownerId;
        if (payment == null) {
            ownerId = verifiedOwner(invoice);
            if (ownerId == null || !hasOnlyVipLines(invoice)) return;
        } else {
            // Keep historical invoices verifiable even after account deletion or price retirement.
            if (!Objects.equals(payment.getStripeCustomerId(), invoice.getCustomer())
                    || !Objects.equals(payment.getStripeSubscriptionId(), invoice.getSubscription())) {
                throw new IllegalStateException("Subscription payment ownership changed");
            }
            ownerId = payment.getUserId();
        }

        String chargeId = invoice.getCharge();
        if (chargeId == null && invoice.getPaymentIntent() != null) {
            chargeId = PaymentIntent.retrieve(invoice.getPaymentIntent(), config.requestOptions()).getLatestCharge();
        }
        if (chargeId == null) return; // Account credit, free invoices, and out-of-band payment are not collected cash.
        Charge charge = Charge.retrieve(chargeId, config.requestOptions());
        if (!Boolean.TRUE.equals(charge.getPaid()) || !Boolean.TRUE.equals(charge.getCaptured())
                || !"succeeded".equals(charge.getStatus()) || !"usd".equals(charge.getCurrency())
                || !Objects.equals(invoice.getCustomer(), charge.getCustomer())
                || !Objects.equals(invoiceId, charge.getInvoice())
                || charge.getAmountCaptured() == null || charge.getAmountCaptured() <= 0) return;
        long paidCents = Math.min(invoice.getAmountPaid(), charge.getAmountCaptured());
        long refundedCents = Math.min(paidCents, Math.max(0, charge.getAmountRefunded() == null ? 0 : charge.getAmountRefunded()));
        if (payment == null) payment = new VipSubscriptionPayment();
        payment.setStripeInvoiceId(invoiceId);
        payment.setUserId(ownerId);
        payment.setStripeCustomerId(invoice.getCustomer());
        payment.setStripeSubscriptionId(invoice.getSubscription());
        payment.setStripeChargeId(chargeId);
        payment.setPaidCents(paidCents);
        payment.setRefundedCents(refundedCents);
        payment.setCurrency("usd");
        payment.setPaidAt(paidAt);
        payment.setBillingReason(invoice.getBillingReason() == null ? "other" : invoice.getBillingReason());
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);
    }

    private Long verifiedOwner(Invoice invoice) throws StripeException {
        Subscription subscription = Subscription.retrieve(invoice.getSubscription(), config.requestOptions());
        Map<String, String> metadata = subscription.getMetadata();
        if (!Objects.equals(invoice.getCustomer(), subscription.getCustomer()) || metadata == null
                || !"vip".equals(metadata.get("allchat_feature"))) return null;
        Long ownerId;
        try {
            ownerId = Long.valueOf(metadata.get("allchat_user_id"));
            if (ownerId <= 0) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        User currentCustomerOwner = users.findByStripeCustomerId(invoice.getCustomer()).orElse(null);
        if (currentCustomerOwner != null && !ownerId.equals(currentCustomerOwner.getId())) return null;
        User owner = users.findById(ownerId).orElse(null);
        if (owner != null && !Boolean.TRUE.equals(owner.getDeleted())
                && !Objects.equals(owner.getStripeCustomerId(), invoice.getCustomer())) return null;
        // Server-authored subscription metadata preserves attribution after soft/hard deletion.
        return ownerId;
    }

    private boolean hasOnlyVipLines(Invoice invoice) throws StripeException {
        if (invoice.getLines() == null) return false;
        var lines = invoice.getLines().list(InvoiceLineItemCollectionListParams.builder().setLimit(100L).build(),
                config.requestOptions());
        int inspected = 0;
        boolean positiveVipLine = false;
        for (InvoiceLineItem line : lines.autoPagingIterable()) {
            if (++inspected > 1000) throw new IllegalStateException("Subscription invoice exceeds reporting line limit");
            if (line.getAmount() == null || line.getAmount() == 0) continue;
            if (!Objects.equals(invoice.getSubscription(), line.getSubscription()) || line.getPrice() == null
                    || config.intervalForPrice(line.getPrice().getId()) == null || !"usd".equals(line.getCurrency())) return false;
            if (line.getAmount() > 0) positiveVipLine = true;
        }
        return positiveVipLine;
    }

    private VipReportingState lockedState() {
        // Webhooks can arrive during application startup, before ApplicationReadyEvent.
        states.initialize(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return states.lockState().orElseThrow();
    }

    private static String id(JsonNode node) {
        return node.isTextual() ? node.asText() : node.path("id").asText(null);
    }
}
