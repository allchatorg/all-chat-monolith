package com.mk3.chatapp.pro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.models.identity.User;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/** On-site billing operations. All mutations serialize with Checkout and webhooks on the user row. */
@Service
@RequiredArgsConstructor
public class ProBillingManagementService {
    private final ProBillingService billing;
    private final ProConfiguration config;
    private final ObjectMapper mapper;
    private static final String CHANGE_OPERATION = "allchat_plan_change";
    private static final String CHANGE_QUOTE = "allchat_plan_quote";
    private static final Set<String> ACTIONABLE_INTENTS = Set.of("requires_action", "requires_confirmation", "requires_payment_method");
    private static final HttpClient PDF_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public record InvoiceRow(String id, String number, String status, String currency, long total,
                             long amountPaid, long amountRemaining, Instant created, Instant periodStart,
                             Instant periodEnd, boolean canPay, boolean canDownload) { }
    public record InvoicePage(List<InvoiceRow> invoices, String nextCursor) { }
    public record PaymentResult(ProSubscriptionSummary subscription, String clientSecret, String invoiceId,
                                String paymentStatus) { }
    public record PlanPreview(String previewToken, String interval, long amountDue, String currency,
                              Instant effectiveAt, Instant nextRenewalAt, boolean immediate, Instant expiresAt) { }
    public record Quote(long userId, String subscriptionId, String state, String interval, String priceId, long prorationDate,
                        long amountDue, String currency, long expiresAt, String operationId) { }

    @Transactional
    public InvoicePage invoices(String startingAfter) throws StripeException {
        billing.requireProvider();
        User user = billing.ownLockedUser();
        if (user.getStripeCustomerId() == null) return new InvoicePage(List.of(), null);
        Set<String> ownedSubscriptions = billing.listProSubscriptions(user).stream().map(Subscription::getId).collect(Collectors.toSet());
        if (ownedSubscriptions.isEmpty()) return new InvoicePage(List.of(), null);
        var params = InvoiceListParams.builder().setCustomer(user.getStripeCustomerId()).setLimit(20L);
        if (startingAfter != null && !startingAfter.isBlank()) {
            // A cursor must belong to this customer, even when it represents a filtered non-Pro invoice.
            Invoice cursor = Invoice.retrieve(startingAfter, config.requestOptions());
            if (!Objects.equals(user.getStripeCustomerId(), cursor.getCustomer())) throw notFound();
            params.setStartingAfter(startingAfter);
        }
        InvoiceCollection page = Invoice.list(params.build(), config.requestOptions());
        List<InvoiceRow> rows = page.getData().stream().filter(invoice -> ownedSubscriptions.contains(invoice.getSubscription()))
                .map(invoice -> new InvoiceRow(invoice.getId(), invoice.getNumber(), invoice.getStatus(), invoice.getCurrency(),
                        amount(invoice.getTotal()), amount(invoice.getAmountPaid()), amount(invoice.getAmountRemaining()),
                        instant(invoice.getCreated()), instant(invoice.getPeriodStart()), instant(invoice.getPeriodEnd()),
                        "open".equals(invoice.getStatus()) && amount(invoice.getAmountRemaining()) > 0,
                        invoice.getInvoicePdf() != null)).toList();
        return new InvoicePage(rows, Boolean.TRUE.equals(page.getHasMore()) && !page.getData().isEmpty()
                ? page.getData().get(page.getData().size() - 1).getId() : null);
    }

    @Transactional
    public byte[] invoicePdf(String invoiceId) throws StripeException {
        billing.requireProvider();
        Invoice invoice = ownedInvoice(billing.ownLockedUser(), invoiceId);
        if (invoice.getInvoicePdf() == null) throw notFound();
        URI url;
        try { url = URI.create(invoice.getInvoicePdf()); }
        catch (IllegalArgumentException e) { throw providerUnavailable(); }
        try {
            for (int redirects = 0; redirects < 4; redirects++) {
                if (!trustedPdfUrl(url)) throw providerUnavailable();
                var response = PDF_CLIENT.send(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                try (var body = response.body()) {
                    if (Set.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                        String location = response.headers().firstValue("location").orElseThrow(ProBillingManagementService::providerUnavailable);
                        url = url.resolve(location);
                        continue;
                    }
                    if (response.statusCode() != 200) throw providerUnavailable();
                    byte[] bytes = body.readNBytes(10 * 1024 * 1024 + 1);
                    if (bytes.length > 10 * 1024 * 1024 || bytes.length < 5
                            || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw providerUnavailable();
                    return bytes;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw providerUnavailable();
        } catch (IOException | IllegalArgumentException e) {
            throw providerUnavailable();
        }
        throw providerUnavailable();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PaymentResult payInvoice(String invoiceId, String selectedPaymentMethodId) throws StripeException {
        billing.requireProvider();
        User user = billing.ownLockedUser();
        Invoice invoice = ownedInvoice(user, invoiceId);
        if ("paid".equals(invoice.getStatus())) return paymentResult(user, invoice);
        if (!"open".equals(invoice.getStatus()) || amount(invoice.getAmountRemaining()) <= 0) {
            throw conflict("This invoice is no longer payable. Refresh billing.");
        }
        PaymentIntent intent = invoice.getPaymentIntentObject();
        if (intent != null && Set.of("processing", "succeeded").contains(intent.getStatus())) {
            return paymentResult(user, invoice);
        }
        Subscription subscription = ownedSubscription(user, invoice.getSubscription());
        String paymentMethod = selectedPaymentMethodId != null ? selectedPaymentMethodId : subscription.getDefaultPaymentMethod();
        if (paymentMethod == null) {
            Customer customer = Customer.retrieve(user.getStripeCustomerId(), config.requestOptions());
            if (customer.getInvoiceSettings() != null) paymentMethod = customer.getInvoiceSettings().getDefaultPaymentMethod();
        }
        if (paymentMethod == null) throw conflict("Add a card and choose it before paying this invoice.");
        PaymentMethod card = PaymentMethod.retrieve(paymentMethod, config.requestOptions());
        if (!Objects.equals(card.getCustomer(), user.getStripeCustomerId()) || !"card".equals(card.getType())) {
            throw conflict("Choose a saved card that belongs to your account before paying this invoice.");
        }
        if (intent != null && "requires_action".equals(intent.getStatus()) && paymentMethod.equals(intent.getPaymentMethod())) {
            return paymentResult(user, invoice);
        }
        try {
            invoice.pay(InvoicePayParams.builder().setPaymentMethod(paymentMethod).setOffSession(false).build(),
                    config.requestOptions("pro-invoice-" + invoiceId + "-" + amount(invoice.getAttemptCount()) + "-" + paymentMethod));
        } catch (CardException e) {
            // The existing invoice PaymentIntent carries the decline / authentication state.
            // Return its scoped client secret so Stripe.js can finish the same payment, never a second charge.
        }
        return paymentResult(user, ownedInvoice(user, invoiceId));
    }

    @Transactional
    public PlanPreview preview(String interval) throws StripeException {
        User user = changeUser();
        Subscription subscription = currentSubscription(user);
        requireChangeable(subscription, interval);
        Quote unfinished = unfinishedQuote(subscription);
        if (unfinished != null && unfinished.expiresAt() >= Instant.now().getEpochSecond()) {
            if (!interval.equals(unfinished.interval())) throw conflict("Your previous plan change is still being confirmed. Refresh billing shortly.");
            // A metadata write or response may have timed out before the paid update. Re-open the
            // original confirmation after a reload so its idempotency key is never abandoned.
            Instant effective = instant(unfinished.prorationDate());
            return new PlanPreview(sign(unfinished), unfinished.interval(), unfinished.amountDue(), unfinished.currency(),
                    effective, effective.atZone(ZoneOffset.UTC).plusYears(1).toInstant(), true, instant(unfinished.expiresAt()));
        }
        long timestamp = Instant.now().getEpochSecond();
        boolean immediate = "YEARLY".equals(interval);
        Invoice upcoming = immediate ? upgradeInvoice(user, subscription, timestamp) : null;
        long due = upcoming == null ? 0 : amount(upcoming.getAmountDue());
        String currency = upcoming == null ? "usd" : upcoming.getCurrency();
        Instant effective = immediate ? instant(timestamp) : instant(subscription.getCurrentPeriodEnd());
        Instant nextRenewal = immediate ? effective.atZone(ZoneOffset.UTC).plusYears(1).toInstant()
                : effective.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        Quote quote = new Quote(user.getId(), subscription.getId(), state(subscription), interval, config.priceId(interval), timestamp,
                due, currency, timestamp + 600, UUID.randomUUID().toString());
        return new PlanPreview(sign(quote), interval, due, currency, effective, nextRenewal, immediate, instant(quote.expiresAt()));
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PaymentResult confirm(String previewToken) throws StripeException {
        User user = changeUser();
        Quote quote = verify(previewToken);
        if (quote.userId() != user.getId()) throw notFound();
        if (quote.expiresAt() < Instant.now().getEpochSecond()) throw staleQuote();
        if (!Objects.equals(quote.priceId(), config.priceId(quote.interval()))) throw staleQuote();
        Subscription subscription = ownedSubscription(user, quote.subscriptionId());
        // A response may be lost after Stripe has applied/started the operation. Reusing a signed
        // confirmation returns that same invoice rather than generating a second proration.
        boolean sameOperation = subscription.getMetadata() != null
                && quote.operationId().equals(subscription.getMetadata().get(CHANGE_OPERATION));
        if (sameOperation && quote.priceId().equals(singleItem(subscription).getPrice().getId())) {
            return paymentResult(user, null);
        }
        if (sameOperation && subscription.getPendingUpdate() != null
                && subscription.getPendingUpdate().getSubscriptionItems() != null
                && subscription.getPendingUpdate().getSubscriptionItems().stream()
                    .anyMatch(item -> item.getPrice() != null && quote.priceId().equals(item.getPrice().getId()))) {
            Invoice pendingInvoice = latestInvoice(user, subscription);
            if (pendingInvoice == null || !"subscription_update".equals(pendingInvoice.getBillingReason())
                    || amount(pendingInvoice.getAmountDue()) != quote.amountDue()
                    || !Objects.equals(pendingInvoice.getCurrency(), quote.currency())) throw staleQuote();
            return paymentResult(user, pendingInvoice);
        }
        if ("YEARLY".equals(quote.interval())) {
            requireChangeable(subscription, quote.interval());
            if (!quote.state().equals(state(subscription))) throw staleQuote();
            Invoice refreshed = upgradeInvoice(user, subscription, quote.prorationDate());
            if (amount(refreshed.getAmountDue()) != quote.amountDue() || !Objects.equals(refreshed.getCurrency(), quote.currency())) throw staleQuote();
            Quote unfinished = unfinishedQuote(subscription);
            if (!sameOperation && unfinished != null && unfinished.expiresAt() >= Instant.now().getEpochSecond()) {
                throw conflict("Your previous plan change is still being confirmed. Review that change before starting another.");
            }
            if (!sameOperation) {
                // API 2023-10-16 restricts the fields accepted with pending_if_incomplete. Persist
                // the recovery marker in a separate ordinary update, never in the paid update.
                // Expired markers can be superseded only after the authoritative unchanged state
                // and absence of a pending update have passed the checks above.
                subscription.update(SubscriptionUpdateParams.builder()
                                .putMetadata(CHANGE_OPERATION, quote.operationId())
                                .putMetadata(CHANGE_QUOTE, quoteJson(quote)).build(),
                        config.requestOptions("pro-plan-prepare-" + quote.operationId()));
            }
            SubscriptionItem item = singleItem(subscription);
            subscription.update(SubscriptionUpdateParams.builder()
                            .addItem(SubscriptionUpdateParams.Item.builder().setId(item.getId())
                                    .setPrice(config.getYearlyPriceId()).setQuantity(1L).build())
                            .setBillingCycleAnchor(SubscriptionUpdateParams.BillingCycleAnchor.NOW)
                            .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                            .setProrationDate(quote.prorationDate())
                            .setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.PENDING_IF_INCOMPLETE).build(),
                    config.requestOptions("pro-plan-upgrade-" + quote.operationId()));
            subscription = ownedSubscription(user, quote.subscriptionId());
            return paymentResult(user, latestInvoice(user, subscription));
        }
        scheduleMonthly(subscription, quote);
        return paymentResult(user, null);
    }

    private void scheduleMonthly(Subscription subscription, Quote quote) throws StripeException {
        SubscriptionSchedule schedule;
        if (subscription.getSchedule() == null) {
            requireChangeable(subscription, "MONTHLY");
            if (!quote.state().equals(state(subscription))) throw staleQuote();
            schedule = SubscriptionSchedule.create(SubscriptionScheduleCreateParams.builder()
                    .setFromSubscription(subscription.getId()).build(), config.requestOptions("pro-plan-schedule-" + quote.operationId()));
        } else {
            // Recover a timeout between schedule creation and configuration using the exact same
            // idempotent create. A different existing schedule cannot be overwritten this way.
            schedule = SubscriptionSchedule.create(SubscriptionScheduleCreateParams.builder()
                    .setFromSubscription(subscription.getId()).build(), config.requestOptions("pro-plan-schedule-" + quote.operationId()));
            if (!Objects.equals(subscription.getSchedule(), schedule.getId())) throw staleQuote();
            schedule = SubscriptionSchedule.retrieve(schedule.getId(), config.requestOptions());
            if (schedule.getMetadata() != null && quote.operationId().equals(schedule.getMetadata().get(CHANGE_OPERATION))) return;
            String actualSchedule = subscription.getSchedule();
            subscription.setSchedule((String) null);
            try {
                requireChangeable(subscription, "MONTHLY");
                if (!quote.state().equals(state(subscription))) throw staleQuote();
            } finally { subscription.setSchedule(actualSchedule); }
        }
        SubscriptionItem item = singleItem(subscription);
        var currentPhase = SubscriptionScheduleUpdateParams.Phase.builder()
                .setStartDate(subscription.getCurrentPeriodStart()).setEndDate(subscription.getCurrentPeriodEnd())
                .setProrationBehavior(SubscriptionScheduleUpdateParams.Phase.ProrationBehavior.NONE)
                .addItem(SubscriptionScheduleUpdateParams.Phase.Item.builder().setPrice(item.getPrice().getId()).setQuantity(1L).build());
        var monthlyPhase = SubscriptionScheduleUpdateParams.Phase.builder()
                .setStartDate(subscription.getCurrentPeriodEnd()).setIterations(1L)
                .setProrationBehavior(SubscriptionScheduleUpdateParams.Phase.ProrationBehavior.NONE)
                .addItem(SubscriptionScheduleUpdateParams.Phase.Item.builder().setPrice(config.getMonthlyPriceId()).setQuantity(1L).build());
        schedule.update(SubscriptionScheduleUpdateParams.builder()
                        .setEndBehavior(SubscriptionScheduleUpdateParams.EndBehavior.RELEASE)
                        .setProrationBehavior(SubscriptionScheduleUpdateParams.ProrationBehavior.NONE)
                        .addPhase(currentPhase.build()).addPhase(monthlyPhase.build())
                        .putMetadata(CHANGE_OPERATION, quote.operationId()).build(),
                config.requestOptions("pro-plan-schedule-phases-" + quote.operationId()));
    }

    private User changeUser() throws StripeException {
        billing.requireAvailable();
        billing.requireYearlyBilling();
        User user = billing.ownLockedUser();
        billing.requireEligible(user);
        billing.validateCatalog();
        return user;
    }

    private Subscription currentSubscription(User user) throws StripeException {
        var projection = billing.projection(user);
        billing.reconcile(user, projection);
        if (projection.getStripeSubscriptionId() == null) throw conflict("There is no subscription to change.");
        return ownedSubscription(user, projection.getStripeSubscriptionId());
    }

    private void requireChangeable(Subscription subscription, String interval) throws StripeException {
        String current = config.intervalForPrice(singleItem(subscription).getPrice().getId());
        if (!Set.of("MONTHLY", "YEARLY").contains(interval) || interval.equals(current)) {
            throw conflict("Choose a different billing interval.");
        }
        if (!"active".equals(subscription.getStatus()) || subscription.getSchedule() != null
                || Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()) || subscription.getPendingUpdate() != null
                || !"charge_automatically".equals(subscription.getCollectionMethod())) {
            throw conflict("Resolve your pending payment, scheduled change, or cancellation before changing plans.");
        }
        if (subscription.getLatestInvoice() == null || !Boolean.TRUE.equals(Invoice.retrieve(subscription.getLatestInvoice(), config.requestOptions()).getPaid())) {
            throw conflict("Pay your outstanding invoice before changing plans.");
        }
        // These plans are deliberately fixed-price. Do not silently discard out-of-band billing
        // adjustments when constructing schedule phases.
        if (subscription.getDiscount() != null || (subscription.getDefaultTaxRates() != null && !subscription.getDefaultTaxRates().isEmpty())
                || (singleItem(subscription).getTaxRates() != null && !singleItem(subscription).getTaxRates().isEmpty())) {
            throw conflict("This subscription has custom billing adjustments. Contact support before changing plans.");
        }
    }

    private Invoice upgradeInvoice(User user, Subscription subscription, long timestamp) throws StripeException {
        return Invoice.upcoming(InvoiceUpcomingParams.builder().setCustomer(user.getStripeCustomerId())
                .setSubscription(subscription.getId())
                .addSubscriptionItem(InvoiceUpcomingParams.SubscriptionItem.builder().setId(singleItem(subscription).getId())
                        .setPrice(config.getYearlyPriceId()).setQuantity(1L).build())
                .setSubscriptionBillingCycleAnchor(InvoiceUpcomingParams.SubscriptionBillingCycleAnchor.NOW)
                .setSubscriptionProrationBehavior(InvoiceUpcomingParams.SubscriptionProrationBehavior.ALWAYS_INVOICE)
                .setSubscriptionProrationDate(timestamp).build(), config.requestOptions());
    }

    private SubscriptionItem singleItem(Subscription subscription) {
        if (subscription.getItems() == null || subscription.getItems().getData().size() != 1) throw conflict("This subscription needs support review.");
        SubscriptionItem item = subscription.getItems().getData().get(0);
        if (item.getPrice() == null || config.intervalForPrice(item.getPrice().getId()) == null || !Long.valueOf(1).equals(item.getQuantity())) {
            throw conflict("This subscription needs support review.");
        }
        return item;
    }

    private Subscription ownedSubscription(User user, String subscriptionId) throws StripeException {
        if (subscriptionId == null || user.getStripeCustomerId() == null) throw notFound();
        Subscription subscription = Subscription.retrieve(subscriptionId, config.requestOptions());
        boolean pro = subscription.getMetadata() != null && "pro".equals(subscription.getMetadata().get("allchat_feature"));
        if (!pro && subscription.getItems() != null) pro = subscription.getItems().getData().stream()
                .anyMatch(item -> item.getPrice() != null && config.intervalForPrice(item.getPrice().getId()) != null);
        if (!pro || !Objects.equals(user.getStripeCustomerId(), subscription.getCustomer())) throw notFound();
        return subscription;
    }

    private Invoice ownedInvoice(User user, String invoiceId) throws StripeException {
        if (invoiceId == null || !invoiceId.matches("in_[A-Za-z0-9]+") || user.getStripeCustomerId() == null) throw notFound();
        Invoice invoice = Invoice.retrieve(invoiceId, InvoiceRetrieveParams.builder().addExpand("payment_intent").build(), config.requestOptions());
        if (!Objects.equals(user.getStripeCustomerId(), invoice.getCustomer())) throw notFound();
        ownedSubscription(user, invoice.getSubscription());
        if (invoice.getPaymentIntentObject() != null
                && !Objects.equals(user.getStripeCustomerId(), invoice.getPaymentIntentObject().getCustomer())) throw notFound();
        return invoice;
    }

    private Invoice latestInvoice(User user, Subscription subscription) throws StripeException {
        return subscription.getLatestInvoice() == null ? null : ownedInvoice(user, subscription.getLatestInvoice());
    }

    private PaymentResult paymentResult(User user, Invoice invoice) {
        PaymentIntent intent = invoice == null ? null : invoice.getPaymentIntentObject();
        String secret = intent != null && ACTIONABLE_INTENTS.contains(intent.getStatus()) ? intent.getClientSecret() : null;
        return new PaymentResult(billing.getOwnSubscription(true), secret, invoice == null ? null : invoice.getId(),
                intent != null ? intent.getStatus() : invoice == null ? null : invoice.getStatus());
    }

    private String state(Subscription subscription) {
        SubscriptionItem item = singleItem(subscription);
        return digest(String.join("|", subscription.getId(), Objects.toString(subscription.getCustomer(), ""),
                item.getId(), item.getPrice().getId(), Objects.toString(item.getQuantity(), ""),
                Objects.toString(subscription.getCurrentPeriodStart(), ""), Objects.toString(subscription.getCurrentPeriodEnd(), ""),
                subscription.getStatus(), Objects.toString(subscription.getCancelAtPeriodEnd(), ""),
                Objects.toString(subscription.getSchedule(), ""), Objects.toString(subscription.getLatestInvoice(), ""),
                Objects.toString(subscription.getDefaultPaymentMethod(), "")));
    }

    private Quote unfinishedQuote(Subscription subscription) {
        if (subscription.getMetadata() == null || subscription.getPendingUpdate() != null) return null;
        String json = subscription.getMetadata().get(CHANGE_QUOTE);
        if (json == null) return null;
        try {
            Quote quote = mapper.readValue(json, Quote.class);
            return Objects.equals(quote.subscriptionId(), subscription.getId())
                    && Objects.equals(quote.operationId(), subscription.getMetadata().get(CHANGE_OPERATION))
                    && "YEARLY".equals(quote.interval()) && quote.state().equals(state(subscription)) ? quote : null;
        } catch (Exception e) { return null; }
    }

    private String quoteJson(Quote quote) {
        try {
            String json = mapper.writeValueAsString(quote);
            if (json.length() > 500) throw providerUnavailable();
            return json;
        } catch (Exception e) { throw providerUnavailable(); }
    }

    private String sign(Quote quote) {
        try {
            String body = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(quote));
            return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(body));
        } catch (Exception e) { throw providerUnavailable(); }
    }

    private Quote verify(String token) {
        try {
            if (token == null || token.length() > 4096) throw staleQuote();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(mac(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) throw staleQuote();
            return mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Quote.class);
        } catch (Exception e) { throw staleQuote(); }
    }

    private byte[] mac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(config.getApiKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(("allchat-pro-plan-v1:" + body).getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw providerUnavailable(); }
    }

    private static boolean trustedPdfUrl(URI url) {
        String host = url.getHost();
        return "https".equals(url.getScheme()) && url.getUserInfo() == null && (url.getPort() == -1 || url.getPort() == 443)
                && host != null && (host.equals("stripe.com") || host.endsWith(".stripe.com")
                || host.equals("stripecdn.com") || host.endsWith(".stripecdn.com"));
    }
    private static long amount(Long value) { return value == null ? 0 : value; }
    private static Instant instant(Long value) { return value == null ? null : Instant.ofEpochSecond(value); }
    private static ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private static ResponseStatusException staleQuote() { return conflict("Your plan preview expired or billing changed. Review a new preview before confirming."); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Billing record not found."); }
    private static ResponseStatusException providerUnavailable() { return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Billing is temporarily unavailable. Please try again."); }
}
