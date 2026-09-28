package com.mk3.chatapp.pro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.models.pro.ProcessedStripeEvent;
import com.mk3.chatapp.models.pro.ProSubscription;
import com.mk3.chatapp.repositories.ProcessedStripeEventRepository;
import com.mk3.chatapp.repositories.ProSubscriptionRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.ProBadgeService;
import com.mk3.chatapp.services.IpService;
import com.mk3.chatapp.services.SecurityService;
import com.mk3.chatapp.services.StripeCustomerService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.billingportal.Configuration;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.mk3.chatapp.utils.IpAddressUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProBillingService {
    private static final Set<String> TERMINAL = Set.of("canceled", "incomplete_expired");
    private static final Set<String> EVENTS = Set.of("checkout.session.completed", "checkout.session.expired",
            "checkout.session.async_payment_succeeded", "checkout.session.async_payment_failed",
            "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted",
            "customer.subscription.paused", "customer.subscription.resumed", "invoice.paid",
            "invoice.payment_failed", "invoice.payment_action_required", "subscription_schedule.created",
            "subscription_schedule.updated", "subscription_schedule.released", "subscription_schedule.completed",
            "subscription_schedule.canceled", "subscription_schedule.aborted");
    private final ProConfiguration config;
    private final SecurityService securityService;
    private final UserRepository users;
    private final ProSubscriptionRepository subscriptions;
    private final ProcessedStripeEventRepository events;
    private final StripeCustomerService customers;
    private final ProBadgeService badges;
    private final ObjectMapper objectMapper;
    private final IpService ipService;
    private final EntityManager entityManager;

    @Transactional
    public ProSubscriptionSummary getOwnSubscription(boolean refresh) {
        User user = ownLockedUser();
        ProSubscription projection = subscriptions.findById(user.getId()).orElse(null);
        if (config.hasApiKey() && projection != null &&
                (refresh || projection.getLastReconciledAt() == null
                        || projection.getLastReconciledAt().isBefore(Instant.now().minusSeconds(15)))) {
            try {
                reconcile(user, projection);
            } catch (StripeException e) {
                // Existing paid access and cancellation state remain readable during an outage.
                log.warn("Pro status refresh failed for user {} ({})", user.getId(), e.getClass().getSimpleName());
            }
        }
        badges.refreshVisibility(user);
        return summary(user, projection);
    }

    // Checked Stripe failures commit the durable checkout attempt so a retry uses the same idempotency key.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String createCheckout(String interval) throws StripeException {
        requireAvailable();
        if ("YEARLY".equals(interval)) requireYearlyBilling();
        User user = ownLockedUser();
        requireEligible(user);
        validateCatalog();
        customers.getOrCreate(user);
        ProSubscription projection = projection(user);
        reconcile(user, projection);
        if (hasOpenSubscription(projection)) {
            if ("INCOMPLETE".equals(projection.getStatus()) && projection.getCheckoutSessionId() != null) {
                Session pending = Session.retrieve(projection.getCheckoutSessionId(), config.requestOptions());
                if ("open".equals(pending.getStatus()) && pending.getUrl() != null
                        && Objects.equals(user.getStripeCustomerId(), pending.getCustomer())
                        && (pending.getSubscription() == null
                            || Objects.equals(projection.getStripeSubscriptionId(), pending.getSubscription()))) {
                    if (interval.equals(projection.getCheckoutInterval())) return pending.getUrl();
                    throw conflict("Continue the existing checkout or cancel it before choosing a different plan.");
                }
            }
            throw conflict("You already have a Pro subscription. Manage it in subscription settings.");
        }

        if (projection.getCheckoutSessionId() != null) {
            Session pending = Session.retrieve(projection.getCheckoutSessionId(), config.requestOptions());
            if ("open".equals(pending.getStatus())) {
                if (interval.equals(projection.getCheckoutInterval())) return pending.getUrl();
                pending.expire(config.requestOptions("pro-expire-" + pending.getId()));
            } else if ("complete".equals(pending.getStatus())) {
                reconcile(user, projection);
                throw conflict("Your payment is being confirmed. Refresh subscription settings shortly.");
            }
            clearCheckout(projection);
        }
        if (projection.getCheckoutExpiresAt() != null && !projection.getCheckoutExpiresAt().isAfter(Instant.now())) {
            clearCheckout(projection);
        }
        if (projection.getCheckoutAttemptId() != null && !interval.equals(projection.getCheckoutInterval())) {
            throw conflict("A previous checkout is still being confirmed. Please retry its selected plan.");
        }
        if (projection.getCheckoutAttemptId() == null) {
            projection.setCheckoutAttemptId(UUID.randomUUID().toString());
            projection.setCheckoutInterval(interval);
            projection.setCheckoutExpiresAt(Instant.now().plusSeconds(3600));
            subscriptions.saveAndFlush(projection);
        }
        var params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCurrency("usd")
                .putExtraParam("adaptive_pricing", Map.of("enabled", false))
                .setCustomer(user.getStripeCustomerId())
                .setClientReferenceId(user.getId().toString())
                .setSuccessUrl(config.returnUrl() + "?pro=subscriptions&checkout=success")
                .setCancelUrl(config.returnUrl() + "?pro=subscriptions&checkout=canceled")
                .setExpiresAt(projection.getCheckoutExpiresAt().getEpochSecond())
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(config.priceId(interval)).setQuantity(1L).build())
                .putMetadata("allchat_feature", "pro")
                .putMetadata("allchat_user_id", user.getId().toString())
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("allchat_feature", "pro")
                        .putMetadata("allchat_user_id", user.getId().toString()).build())
                .build();
        Session checkout = Session.create(params,
                config.requestOptions("pro-checkout-" + projection.getCheckoutAttemptId()));
        projection.setCheckoutSessionId(checkout.getId());
        subscriptions.save(projection);
        if (!"open".equals(checkout.getStatus()) || checkout.getUrl() == null) {
            reconcile(user, projection);
            throw conflict("Your checkout has already completed or expired. Refresh subscription settings.");
        }
        return checkout.getUrl();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String createPortal(boolean switchPlan) throws StripeException {
        requireProvider();
        User user = ownLockedUser();
        ProSubscription projection = subscriptions.findById(user.getId()).orElse(null);
        if (user.getStripeCustomerId() == null || projection == null) {
            throw conflict("There is no Pro billing account to manage yet.");
        }
        String configurationId = switchPlan ? config.getSwitchPortalConfigurationId()
                : config.getBillingPortalConfigurationId();
        if (configurationId == null || configurationId.isBlank()) throw unavailable();
        if (switchPlan) {
            requireAvailable();
            // This portal exposes both prices, so issuing it while yearly sales are off
            // would let a monthly subscriber bypass the checkout feature flag.
            requireYearlyBilling();
            requireEligible(user);
            validateCatalog();
            reconcile(user, projection);
            if (!summary(user, projection).canChangePlan()) {
                throw conflict("Undo the scheduled change or cancellation before switching plans.");
            }
        }
        validatePortalConfiguration(configurationId, switchPlan);
        var params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(user.getStripeCustomerId()).setConfiguration(configurationId)
                .setReturnUrl(config.returnUrl() + "?pro=subscriptions&billing=updated");
        if (switchPlan) {
            params.setFlowData(com.stripe.param.billingportal.SessionCreateParams.FlowData.builder()
                    .setType(com.stripe.param.billingportal.SessionCreateParams.FlowData.Type.SUBSCRIPTION_UPDATE)
                    .setSubscriptionUpdate(com.stripe.param.billingportal.SessionCreateParams.FlowData.SubscriptionUpdate
                            .builder().setSubscription(projection.getStripeSubscriptionId()).build())
                    .setAfterCompletion(com.stripe.param.billingportal.SessionCreateParams.FlowData.AfterCompletion.builder()
                            .setType(com.stripe.param.billingportal.SessionCreateParams.FlowData.AfterCompletion.Type.REDIRECT)
                            .setRedirect(com.stripe.param.billingportal.SessionCreateParams.FlowData.AfterCompletion.Redirect
                                    .builder().setReturnUrl(config.returnUrl() + "?pro=subscriptions&billing=updated").build())
                            .build()).build());
        }
        return com.stripe.model.billingportal.Session.create(params.build(), config.requestOptions()).getUrl();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ProSubscriptionSummary cancel() throws StripeException {
        requireProvider();
        User user = ownLockedUser();
        ProSubscription projection = projection(user);
        // Close outstanding payment links before reading the subscription: Checkout may otherwise
        // complete between the initial read and cancellation and start an uncanceled renewal.
        expireCheckout(projection);
        reconcile(user, projection);
        Subscription subscription = existingSubscription(projection);
        if (subscription != null && !TERMINAL.contains(subscription.getStatus())) {
            releaseSchedule(subscription);
            if ("incomplete".equals(subscription.getStatus())) {
                // Stripe does not allow cancel_at_period_end updates before the first payment succeeds.
                subscription.cancel(SubscriptionCancelParams.builder().setInvoiceNow(false).setProrate(false).build(),
                        config.requestOptions("pro-cancel-incomplete-" + subscription.getId()));
            } else if (!Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
                subscription.update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build(),
                        config.requestOptions("pro-cancel-" + UUID.randomUUID()));
            }
        }
        reconcile(user, projection);
        return summary(user, projection);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ProSubscriptionSummary resume() throws StripeException {
        requireAvailable();
        User user = ownLockedUser();
        requireEligible(user);
        ProSubscription projection = projection(user);
        reconcile(user, projection);
        Subscription subscription = existingSubscription(projection);
        if (subscription == null || TERMINAL.contains(subscription.getStatus())) {
            throw conflict("This subscription has ended. Choose a plan to subscribe again.");
        }
        if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
            subscription.update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(false).build(),
                    config.requestOptions("pro-resume-" + UUID.randomUUID()));
        }
        reconcile(user, projection);
        return summary(user, projection);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ProSubscriptionSummary undoScheduledChange() throws StripeException {
        requireProvider();
        User user = ownLockedUser();
        ProSubscription projection = projection(user);
        reconcile(user, projection);
        Subscription subscription = existingSubscription(projection);
        if (subscription != null && !TERMINAL.contains(subscription.getStatus())) releaseSchedule(subscription);
        reconcile(user, projection);
        return summary(user, projection);
    }

    /** Called before an account loses its credentials. A provider failure prevents account deletion. */
    @Transactional
    public void cancelForAccountDeletion(User user) {
        User locked = users.findByIdForUpdate(user.getId()).orElseThrow();
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        ProSubscription projection = subscriptions.findById(locked.getId()).orElse(null);
        if (projection == null) return;
        requireProvider();
        try {
            // Expire links first: a valid checkout must never survive account deletion.
            expireCheckout(projection);
            if (locked.getStripeCustomerId() != null) {
                for (Subscription subscription : listProSubscriptions(locked)) {
                    if (TERMINAL.contains(subscription.getStatus())) continue;
                    releaseSchedule(subscription);
                    subscription.cancel(SubscriptionCancelParams.builder().setInvoiceNow(false).setProrate(false).build(),
                            config.requestOptions("pro-delete-" + subscription.getId()));
                }
            }
            projection.setStatus("CANCELED");
            projection.setCancelAtPeriodEnd(false);
            projection.setPaidThrough(null);
            projection.setStripeScheduleId(null);
            projection.setScheduledInterval(null);
            projection.setScheduledChangeAt(null);
            subscriptions.save(projection);
            badges.updateEntitlement(locked, null);
        } catch (StripeException e) {
            log.warn("Pro cancellation before deletion failed for user {} ({})", user.getId(), e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "We could not confirm that billing stopped. Please retry account deletion.");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleWebhook(String payload, String signature) throws StripeException {
        if (config.getWebhookSecret() == null || config.getWebhookSecret().isBlank()) throw unavailable();
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe signature");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, config.getWebhookSecret());
        } catch (SignatureVerificationException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook");
        }
        if (!EVENTS.contains(event.getType()) || events.existsById(event.getId())) return;
        JsonNode object;
        try {
            object = objectMapper.readTree(payload).path("data").path("object");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook payload");
        }
        String customerId = object.path("customer").isTextual()
                ? object.path("customer").asText() : object.path("customer").path("id").asText(null);
        if (customerId == null) return;
        User owner = users.findByStripeCustomerId(customerId).orElse(null);
        if (owner == null) return;
        User user = users.findByIdForUpdate(owner.getId()).orElseThrow();
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        // All events for an account serialize under the same lock; only mark after successful reconciliation.
        if (events.existsById(event.getId())) return;
        ProSubscription projection = subscriptions.findById(user.getId()).orElse(null);
        if (projection == null && !"pro".equals(object.path("metadata").path("allchat_feature").asText())) return;
        if (projection == null) projection = projection(user);
        requireProvider();
        reconcile(user, projection);
        if (Boolean.TRUE.equals(user.getDeleted())) {
            // A checkout completion racing account deletion must not leave a recurring charge behind.
            cancelForAccountDeletion(user);
        }
        events.save(new ProcessedStripeEvent(event.getId()));
    }

    @Transactional
    public void reconcileAccount(Long userId) {
        User user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null) return;
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        ProSubscription projection = subscriptions.findById(userId).orElse(null);
        if (projection == null) return;
        try {
            if (config.hasApiKey()) reconcile(user, projection);
        } catch (StripeException e) {
            log.warn("Pro reconciliation failed for user {} ({})", userId, e.getClass().getSimpleName());
        } finally {
            // Time-based entitlement is enforced even if Stripe or its webhook delivery is unavailable.
            badges.refreshVisibility(user);
            // Rotate failed accounts too, so an outage cannot starve the rest of a reconciliation batch.
            projection.setLastReconciledAt(Instant.now());
            subscriptions.save(projection);
        }
    }

    private void reconcile(User user, ProSubscription projection) throws StripeException {
        if (user.getStripeCustomerId() == null) return;
        if (!config.isYearlyBillingEnabled() && "YEARLY".equals(projection.getCheckoutInterval())
                && projection.getCheckoutSessionId() != null) {
            Session checkout = Session.retrieve(projection.getCheckoutSessionId(), config.requestOptions());
            if ("open".equals(checkout.getStatus())) {
                checkout.expire(config.requestOptions("pro-expire-" + checkout.getId()));
                clearCheckout(projection);
            }
        }
        List<Subscription> remote = listProSubscriptions(user);
        List<Subscription> open = remote.stream().filter(s -> !TERMINAL.contains(s.getStatus())).toList();
        if (open.size() > 1) {
            log.error("Multiple Pro subscriptions require review for user {}", user.getId());
            throw conflict("Multiple subscriptions need support review. Please contact support before changing billing.");
        }
        Subscription subscription = !open.isEmpty() ? open.get(0) : remote.stream()
                .max(Comparator.comparing(Subscription::getCreated)).orElse(null);
        if (subscription != null) {
            applySubscription(user, projection, subscription);
        }
        if (projection.getCheckoutSessionId() != null) {
            Session checkout = Session.retrieve(projection.getCheckoutSessionId(), config.requestOptions());
            if ("expired".equals(checkout.getStatus()) ||
                    ("complete".equals(checkout.getStatus()) && subscription != null)) clearCheckout(projection);
        } else if (projection.getCheckoutExpiresAt() != null && !projection.getCheckoutExpiresAt().isAfter(Instant.now())) {
            clearCheckout(projection);
        }
        projection.setLastReconciledAt(Instant.now());
        subscriptions.save(projection);
    }

    private List<Subscription> listProSubscriptions(User user) throws StripeException {
        var result = new java.util.ArrayList<Subscription>();
        var collection = Subscription.list(SubscriptionListParams.builder().setCustomer(user.getStripeCustomerId())
                .setStatus(SubscriptionListParams.Status.ALL).setLimit(100L).build(), config.requestOptions());
        for (Subscription subscription : collection.autoPagingIterable()) {
            boolean known = subscription.getItems() != null && subscription.getItems().getData().stream()
                    .anyMatch(item -> item.getPrice() != null && config.intervalForPrice(item.getPrice().getId()) != null);
            boolean tagged = subscription.getMetadata() != null && "pro".equals(subscription.getMetadata().get("allchat_feature"));
            if (known || tagged) result.add(subscription);
        }
        return result;
    }

    private void applySubscription(User user, ProSubscription projection, Subscription subscription) throws StripeException {
        if (!Objects.equals(user.getStripeCustomerId(), subscription.getCustomer())) {
            throw conflict("The billing account could not be verified.");
        }
        String interval = null;
        if (subscription.getItems() != null && subscription.getItems().getData().size() == 1) {
            SubscriptionItem item = subscription.getItems().getData().get(0);
            if (Long.valueOf(1).equals(item.getQuantity()) && item.getPrice() != null) {
                interval = config.intervalForPrice(item.getPrice().getId());
            }
        }
        projection.setStripeSubscriptionId(subscription.getId());
        projection.setStatus(subscription.getStatus().toUpperCase(Locale.ROOT));
        projection.setBillingInterval(interval);
        projection.setCurrentPeriodEnd(instant(subscription.getCurrentPeriodEnd()));
        projection.setCancelAtPeriodEnd(Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
        projection.setStripeScheduleId(subscription.getSchedule());
        projection.setScheduledInterval(null);
        projection.setScheduledChangeAt(null);
        if (subscription.getSchedule() != null) {
            SubscriptionSchedule schedule = SubscriptionSchedule.retrieve(subscription.getSchedule(), config.requestOptions());
            if ("active".equals(schedule.getStatus()) && schedule.getPhases() != null) {
                schedule.getPhases().stream()
                        .filter(phase -> phase.getStartDate() != null && phase.getStartDate() > Instant.now().getEpochSecond())
                        .min(Comparator.comparing(SubscriptionSchedule.Phase::getStartDate))
                        .ifPresent(phase -> {
                            if (phase.getItems() != null && phase.getItems().size() == 1) {
                                String next = config.intervalForPrice(phase.getItems().get(0).getPrice());
                                if (next != null) {
                                    projection.setScheduledInterval(next);
                                    projection.setScheduledChangeAt(instant(phase.getStartDate()));
                                }
                            }
                        });
            }
        }
        Instant paidThrough = null;
        if (interval != null && !TERMINAL.contains(subscription.getStatus()) && !"paused".equals(subscription.getStatus())) {
            // An active/past_due subscription alone is not evidence of a paid renewal.
            var invoices = Invoice.list(InvoiceListParams.builder().setSubscription(subscription.getId())
                    .setStatus(InvoiceListParams.Status.PAID).setLimit(10L).build(), config.requestOptions());
            for (Invoice invoice : invoices.getData()) {
                if (!Boolean.TRUE.equals(invoice.getPaid()) || !Objects.equals(invoice.getCustomer(), user.getStripeCustomerId())) continue;
                for (InvoiceLineItem line : invoice.getLines().getData()) {
                    if (line.getPrice() == null || config.intervalForPrice(line.getPrice().getId()) == null
                            || line.getAmount() == null || line.getAmount() < 0 || line.getPeriod() == null) continue;
                    Instant end = instant(line.getPeriod().getEnd());
                    if (end != null && (paidThrough == null || paidThrough.isBefore(end))) paidThrough = end;
                }
            }
        }
        if (Boolean.TRUE.equals(user.getDeleted())) paidThrough = null;
        projection.setPaidThrough(paidThrough);
        badges.updateEntitlement(user, paidThrough);
    }

    private void releaseSchedule(Subscription subscription) throws StripeException {
        if (subscription.getSchedule() == null) return;
        SubscriptionSchedule schedule = SubscriptionSchedule.retrieve(subscription.getSchedule(), config.requestOptions());
        if (Set.of("active", "not_started").contains(schedule.getStatus())) {
            schedule.release(SubscriptionScheduleReleaseParams.builder().setPreserveCancelDate(true).build(),
                    config.requestOptions("pro-release-" + schedule.getId()));
        }
    }

    private void expireCheckout(ProSubscription projection) throws StripeException {
        if (projection.getCheckoutSessionId() != null) {
            Session checkout = Session.retrieve(projection.getCheckoutSessionId(), config.requestOptions());
            if ("open".equals(checkout.getStatus())) checkout.expire(config.requestOptions("pro-expire-" + checkout.getId()));
            clearCheckout(projection);
        } else if (projection.getCheckoutAttemptId() != null && projection.getCheckoutExpiresAt() != null
                && projection.getCheckoutExpiresAt().isAfter(Instant.now())) {
            // A timed-out create may have succeeded remotely; recover its session before allowing deletion/cancel.
            throw conflict("A checkout is still being confirmed. Refresh billing shortly, then retry cancellation.");
        } else {
            clearCheckout(projection);
        }
    }

    private Subscription existingSubscription(ProSubscription projection) throws StripeException {
        return projection.getStripeSubscriptionId() == null ? null
                : Subscription.retrieve(projection.getStripeSubscriptionId(), config.requestOptions());
    }

    private void clearCheckout(ProSubscription projection) {
        projection.setCheckoutSessionId(null);
        projection.setCheckoutAttemptId(null);
        projection.setCheckoutInterval(null);
        projection.setCheckoutExpiresAt(null);
    }

    private void validateCatalog() throws StripeException {
        Price monthly = Price.retrieve(config.getMonthlyPriceId(), config.requestOptions());
        if (!validPrice(monthly, 500L, "month") || monthly.getProduct() == null) throw unavailable();
        validatePortalConfiguration(config.getBillingPortalConfigurationId(), false);
        if (config.isYearlyBillingEnabled()) {
            Price yearly = Price.retrieve(config.getYearlyPriceId(), config.requestOptions());
            if (!validPrice(yearly, 5000L, "year") || !monthly.getProduct().equals(yearly.getProduct())) {
                throw unavailable();
            }
            validatePortalConfiguration(config.getSwitchPortalConfigurationId(), true);
        }
    }

    private boolean validPrice(Price price, long amount, String interval) {
        return Boolean.TRUE.equals(price.getActive()) && "usd".equals(price.getCurrency())
                && Long.valueOf(amount).equals(price.getUnitAmount()) && price.getRecurring() != null
                && interval.equals(price.getRecurring().getInterval())
                && Long.valueOf(1).equals(price.getRecurring().getIntervalCount());
    }

    private void validatePortalConfiguration(String id, boolean switching) throws StripeException {
        Configuration portal = Configuration.retrieve(id,
                com.stripe.param.billingportal.ConfigurationRetrieveParams.builder()
                        .addExpand("features.subscription_update.products").build(), config.requestOptions());
        if (!Boolean.TRUE.equals(portal.getActive()) || portal.getFeatures() == null) throw unavailable();
        var update = portal.getFeatures().getSubscriptionUpdate();
        var cancellation = portal.getFeatures().getSubscriptionCancel();
        if (cancellation != null && Boolean.TRUE.equals(cancellation.getEnabled())) throw unavailable();
        if (!switching) {
            if (update != null && Boolean.TRUE.equals(update.getEnabled())) throw unavailable();
            return;
        }
        if (update == null || !Boolean.TRUE.equals(update.getEnabled())
                || !"always_invoice".equals(update.getProrationBehavior())
                || update.getDefaultAllowedUpdates() == null
                || !Set.copyOf(update.getDefaultAllowedUpdates()).equals(Set.of("price"))
                || update.getProducts() == null || update.getProducts().size() != 1
                || !Set.copyOf(update.getProducts().get(0).getPrices())
                    .equals(Set.of(config.getMonthlyPriceId(), config.getYearlyPriceId()))) throw unavailable();
        // This newer field is retained in the raw response by SDK 24.12, despite lacking a typed accessor.
        try {
            JsonNode raw = objectMapper.readTree(portal.getRawJsonObject().toString());
            JsonNode conditions = raw.path("features").path("subscription_update")
                    .path("schedule_at_period_end").path("conditions");
            if (!conditions.isArray() || conditions.size() != 1
                    || !"shortening_interval".equals(conditions.get(0).path("type").asText())) throw unavailable();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        }
    }

    private ProSubscriptionSummary summary(User user, ProSubscription projection) {
        boolean available = config.billingAvailable();
        boolean eligible = eligible(user);
        boolean pending = projection != null && projection.getCheckoutAttemptId() != null
                && projection.getCheckoutExpiresAt() != null && projection.getCheckoutExpiresAt().isAfter(Instant.now());
        boolean open = projection != null && hasOpenSubscription(projection);
        boolean canceling = projection != null && projection.isCancelAtPeriodEnd();
        boolean active = projection != null && "ACTIVE".equals(projection.getStatus());
        boolean pendingDisabledYearly = pending && !config.isYearlyBillingEnabled()
                && "YEARLY".equals(projection.getCheckoutInterval());
        boolean continueCheckout = available && eligible && pending && projection.getCheckoutInterval() != null
                && !pendingDisabledYearly
                && (!open || ("INCOMPLETE".equals(projection.getStatus()) && projection.getCheckoutSessionId() != null));
        return new ProSubscriptionSummary(available, config.isYearlyBillingEnabled(),
                available && eligible && !open && !pendingDisabledYearly,
                config.hasApiKey() && user.getStripeCustomerId() != null && projection != null
                        && config.getBillingPortalConfigurationId() != null && !config.getBillingPortalConfigurationId().isBlank(),
                available && config.isYearlyBillingEnabled() && eligible && active && !canceling
                        && projection.getStripeScheduleId() == null,
                available && eligible && open && canceling,
                projection == null ? "NONE" : pending && !open ? "PENDING" : projection.getStatus(),
                projection == null ? null : projection.getBillingInterval(), user.isProActive(), user.isShowProBadge(),
                user.isProBadgeVisible(), user.getProBadgeRevision(),
                projection == null ? null : projection.getCurrentPeriodEnd(), user.getProPaidThrough(), canceling,
                projection == null ? null : projection.getScheduledInterval(),
                projection == null ? null : projection.getScheduledChangeAt(), pending,
                pending ? projection.getCheckoutInterval() : null, continueCheckout);
    }

    private boolean hasOpenSubscription(ProSubscription projection) {
        return projection.getStripeSubscriptionId() != null
                && !Set.of("CANCELED", "INCOMPLETE_EXPIRED", "NONE").contains(projection.getStatus());
    }

    private User ownLockedUser() {
        User authenticated = securityService.getCurrentUser();
        if (authenticated == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to manage Pro");
        User user = users.findByIdForUpdate(authenticated.getId()).orElseThrow();
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user;
    }

    private ProSubscription projection(User user) {
        return subscriptions.findById(user.getId()).orElseGet(() -> {
            ProSubscription subscription = new ProSubscription();
            subscription.setUserId(user.getId());
            return subscriptions.save(subscription);
        });
    }

    private boolean eligible(User user) {
        boolean eligible = user.isClaimed() && !user.isBanned() && !Boolean.TRUE.equals(user.getDeleted())
                && (user.getIdVerificationStatus() == null || user.getIdVerificationStatus() == IdVerificationStatus.NONE
                    || user.getIdVerificationStatus() == IdVerificationStatus.VERIFIED);
        if (!eligible) return false;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var required = ipService.getRequiredVerification(IpAddressUtils.getClientIpAddress(attributes.getRequest()));
            return switch (required) {
                case NONE -> true;
                case EMAIL -> user.isVerified();
                case PHONE -> user.getPhoneNumberVerificationDate() != null;
            };
        }
        return true;
    }

    private void requireEligible(User user) {
        if (!eligible(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Claim your account and complete any required verification before subscribing.");
    }

    private void requireAvailable() {
        if (!config.billingAvailable()) throw unavailable();
    }

    private void requireYearlyBilling() {
        if (!config.isYearlyBillingEnabled()) {
            throw conflict("Yearly billing is not available right now. Please choose the monthly plan.");
        }
    }

    private void requireProvider() {
        if (!config.hasApiKey()) throw unavailable();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Pro billing is not available right now. Please try again later.");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private Instant instant(Long seconds) {
        return seconds == null ? null : Instant.ofEpochSecond(seconds);
    }
}
