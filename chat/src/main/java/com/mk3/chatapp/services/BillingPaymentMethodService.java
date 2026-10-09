package com.mk3.chatapp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.pro.ProConfiguration;
import com.mk3.chatapp.repositories.ProSubscriptionRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSchedule;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.SubscriptionScheduleListParams;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Customer-bound card setup and removal rules shared by Ads and Pro. */
@Service
@RequiredArgsConstructor
public class BillingPaymentMethodService {
    private static final Set<String> ENDED = Set.of("canceled", "incomplete_expired");
    private final ProConfiguration config;
    private final StripeCustomerService customers;
    private final UserRepository users;
    private final ProSubscriptionRepository proSubscriptions;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public record CardView(String id, String brand, String last4, Long expMonth, Long expYear,
                           String cardholderName, boolean isDefault, boolean canRemove, String removalReason) { }
    public record SetupResponse(String id, String clientSecret) { }

    @Transactional
    public List<CardView> list(User authenticated) throws StripeException {
        User user = lockUser(authenticated);
        requireProvider();
        return listLocked(user);
    }

    @Transactional
    public SetupResponse createSetup(User authenticated, String paymentMethodId) throws StripeException {
        User user = lockUser(authenticated);
        requireProvider();
        if (!user.isClaimed()) throw forbidden("Claim your account before saving a card.");
        String customerId = customers.getOrCreate(user);
        if (paymentMethodId != null) ownedCard(user, paymentMethodId);
        var params = SetupIntentCreateParams.builder()
                        .setCustomer(customerId)
                        .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                        .addPaymentMethodType("card")
                        .putMetadata("allchat_user_id", user.getId().toString())
                        .putMetadata("allchat_purpose", "saved-card");
        if (paymentMethodId != null) params.setPaymentMethod(paymentMethodId);
        SetupIntent setup = SetupIntent.create(params.build(),
                config.requestOptions("allchat-card-setup-" + UUID.randomUUID()));
        return new SetupResponse(setup.getId(), setup.getClientSecret());
    }

    @Transactional
    public List<CardView> completeSetup(User authenticated, String setupIntentId, boolean makeDefault)
            throws StripeException {
        User user = lockUser(authenticated);
        requireProvider();
        SetupIntent setup = SetupIntent.retrieve(setupIntentId, config.requestOptions());
        if (!Objects.equals(user.getStripeCustomerId(), setup.getCustomer())
                || !user.getId().toString().equals(setup.getMetadata().get("allchat_user_id"))
                || !"saved-card".equals(setup.getMetadata().get("allchat_purpose"))) {
            throw forbidden("This card setup does not belong to your account.");
        }
        if (!"succeeded".equals(setup.getStatus()) || !"off_session".equals(setup.getUsage())
                || setup.getPaymentMethod() == null) {
            throw conflict("Finish verifying your card before saving it.");
        }
        PaymentMethod card = ownedCard(user, setup.getPaymentMethod());
        if (makeDefault) setDefaultLocked(user, card);
        return listLocked(user);
    }

    @Transactional
    public List<CardView> setDefault(User authenticated, String paymentMethodId) throws StripeException {
        User user = lockUser(authenticated);
        requireProvider();
        PaymentMethod card = ownedCard(user, paymentMethodId);
        // An older attached Ads card may not have been set up for off-session usage.
        // Confirming a fresh SetupIntent for it allows Stripe to perform any required authentication.
        boolean succeeded = false;
        for (SetupIntent setup : SetupIntent.list(com.stripe.param.SetupIntentListParams.builder()
                .setCustomer(user.getStripeCustomerId()).setPaymentMethod(card.getId()).setLimit(100L).build(),
                config.requestOptions()).autoPagingIterable()) {
            if ("succeeded".equals(setup.getStatus()) && "off_session".equals(setup.getUsage())) {
                succeeded = true;
                break;
            }
        }
        if (!succeeded) throw conflict("Verify this card for recurring payments before using it for renewal.");
        setDefaultLocked(user, card);
        return listLocked(user);
    }

    @Transactional
    public void remove(User authenticated, String paymentMethodId) throws StripeException {
        User user = lockUser(authenticated);
        requireProvider();
        PaymentMethod card = ownedCard(user, paymentMethodId);
        List<CardView> cards = listLocked(user);
        CardView view = cards.stream().filter(candidate -> candidate.id().equals(card.getId())).findFirst()
                .orElseThrow(() -> forbidden("This card does not belong to your account."));
        if (!view.canRemove()) throw conflict(view.removalReason());
        card.detach(config.requestOptions("allchat-card-remove-" + card.getId()));
    }

    /** Keep older Ads clients working while requiring Stripe to verify new saved cards. */
    @Transactional
    public void saveLegacyCard(User authenticated, String paymentMethodId) throws StripeException {
        User user = lockUser(authenticated);
        requireProvider();
        if (!user.isClaimed()) throw forbidden("Claim your account before saving a card.");
        String customerId = customers.getOrCreate(user);
        PaymentMethod card = PaymentMethod.retrieve(paymentMethodId, config.requestOptions());
        if (card.getCard() == null || (card.getCustomer() != null && !customerId.equals(card.getCustomer()))) {
            throw forbidden("This card does not belong to your account.");
        }
        if (customerId.equals(card.getCustomer())) return;
        SetupIntent setup = SetupIntent.create(SetupIntentCreateParams.builder()
                .setCustomer(customerId).setPaymentMethod(card.getId()).setConfirm(true)
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION).addPaymentMethodType("card")
                .putMetadata("allchat_user_id", user.getId().toString())
                .putMetadata("allchat_purpose", "saved-card").build(),
                config.requestOptions("allchat-legacy-card-" + user.getId() + "-" + card.getId()));
        if (!"succeeded".equals(setup.getStatus())) {
            throw conflict("Your bank needs to verify this card. Refresh this page and add the card again.");
        }
    }

    private void setDefaultLocked(User user, PaymentMethod card) throws StripeException {
        String subscriptionId = proSubscriptions.findById(user.getId())
                .map(pro -> pro.getStripeSubscriptionId()).orElse(null);
        if (subscriptionId == null) throw conflict("There is no renewing VIP subscription to update.");
        Subscription subscription = Subscription.retrieve(subscriptionId, config.requestOptions());
        boolean isPro = subscription.getMetadata() != null
                && "pro".equals(subscription.getMetadata().get("allchat_feature"));
        if (!isPro && subscription.getItems() != null) isPro = subscription.getItems().getData().stream()
                .anyMatch(item -> item.getPrice() != null && config.intervalForPrice(item.getPrice().getId()) != null);
        if (!Objects.equals(user.getStripeCustomerId(), subscription.getCustomer())
                || !isPro) {
            throw forbidden("This subscription does not belong to your account.");
        }
        if (ENDED.contains(subscription.getStatus())) throw conflict("This subscription has ended.");
        // A schedule can override subscription defaults when its next phase starts.
        // Update it first; retries also repair a failure between these two Stripe writes.
        if (subscription.getSchedule() != null) {
            SubscriptionSchedule schedule = SubscriptionSchedule.retrieve(subscription.getSchedule(), config.requestOptions());
            if (!Objects.equals(user.getStripeCustomerId(), schedule.getCustomer())) {
                throw forbidden("This schedule does not belong to your account.");
            }
            List<Map<String, Object>> phases = new ArrayList<>();
            for (var phase : schedule.getPhases()) {
                if (phase.getEndDate() != null && phase.getEndDate() <= java.time.Instant.now().getEpochSecond()) continue;
                phases.add(phaseWithCard(phase, card.getId()));
            }
            if (phases.isEmpty()) throw conflict("Your subscription schedule is changing. Refresh billing and try again.");
            schedule.update(Map.of("default_settings", Map.of("default_payment_method", card.getId()),
                    "phases", phases, "proration_behavior", "none"),
                    config.requestOptions("allchat-scheduled-card-" + UUID.randomUUID()));
        }
        if (!card.getId().equals(subscription.getDefaultPaymentMethod())) {
            subscription.update(SubscriptionUpdateParams.builder().setDefaultPaymentMethod(card.getId()).build(),
                    config.requestOptions("allchat-renewal-card-" + UUID.randomUUID()));
        }
    }

    /** Preserve current/future phase terms when changing only their payment method. */
    private Map<String, Object> phaseWithCard(SubscriptionSchedule.Phase phase, String cardId) {
        try {
            Map<String, Object> values = objectMapper.readValue(phase.toJson(), new TypeReference<>() { });
            values.values().removeIf(Objects::isNull);
            values.put("default_payment_method", cardId);
            values.put("items", phase.getItems().stream().map(item -> {
                Map<String, Object> result = new HashMap<>();
                result.put("price", item.getPrice());
                if (item.getQuantity() != null) result.put("quantity", item.getQuantity());
                if (item.getMetadata() != null && !item.getMetadata().isEmpty()) result.put("metadata", item.getMetadata());
                if (item.getTaxRates() != null && !item.getTaxRates().isEmpty()) {
                    result.put("tax_rates", item.getTaxRates().stream().map(com.stripe.model.TaxRate::getId).toList());
                }
                if (item.getBillingThresholds() != null) {
                    result.put("billing_thresholds", Map.of("usage_gte", item.getBillingThresholds().getUsageGte()));
                }
                return result;
            }).toList());
            if (phase.getCoupon() != null) values.put("coupon", phase.getCoupon());
            if (phase.getOnBehalfOf() != null) values.put("on_behalf_of", phase.getOnBehalfOf());
            if (phase.getDefaultTaxRates() != null) {
                values.put("default_tax_rates", phase.getDefaultTaxRates().stream().map(com.stripe.model.TaxRate::getId).toList());
            }
            if (phase.getAddInvoiceItems() != null) {
                values.put("add_invoice_items", phase.getAddInvoiceItems().stream().map(item -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("price", item.getPrice());
                    if (item.getQuantity() != null) result.put("quantity", item.getQuantity());
                    if (item.getTaxRates() != null) result.put("tax_rates",
                            item.getTaxRates().stream().map(com.stripe.model.TaxRate::getId).toList());
                    return result;
                }).toList());
            }
            return values;
        } catch (java.io.IOException failure) {
            throw conflict("The scheduled plan could not be updated. Please try again.");
        }
    }

    private List<CardView> listLocked(User user) throws StripeException {
        String customerId = user.getStripeCustomerId();
        if (customerId == null || customerId.isBlank()) return List.of();
        List<PaymentMethod> cards = new ArrayList<>();
        for (PaymentMethod card : PaymentMethod.list(PaymentMethodListParams.builder()
                .setCustomer(customerId).setType(PaymentMethodListParams.Type.CARD).setLimit(100L).build(),
                config.requestOptions()).autoPagingIterable()) cards.add(card);
        Customer customer = Customer.retrieve(customerId, config.requestOptions());
        String customerDefault = customer.getInvoiceSettings() == null ? null
                : customer.getInvoiceSettings().getDefaultPaymentMethod();
        String proId = proSubscriptions.findById(user.getId()).map(pro -> pro.getStripeSubscriptionId()).orElse(null);
        String proDefault = null;
        boolean hasSubscriptionWithoutDefault = false;
        Map<String, String> protectedCards = new HashMap<>();
        for (Subscription subscription : Subscription.list(SubscriptionListParams.builder()
                .setCustomer(customerId).setStatus(SubscriptionListParams.Status.ALL).setLimit(100L).build(),
                config.requestOptions()).autoPagingIterable()) {
            if (ENDED.contains(subscription.getStatus())) continue;
            String renewalCard = subscription.getDefaultPaymentMethod() != null
                    ? subscription.getDefaultPaymentMethod() : customerDefault;
            if (renewalCard != null) protectedCards.put(renewalCard,
                    "Choose another renewal card before removing this card.");
            else hasSubscriptionWithoutDefault = true;
            if (subscription.getId().equals(proId)) proDefault = renewalCard;
        }
        for (Invoice invoice : Invoice.list(InvoiceListParams.builder().setCustomer(customerId)
                .setStatus(InvoiceListParams.Status.OPEN).setLimit(100L).addExpand("data.payment_intent").build(),
                config.requestOptions()).autoPagingIterable()) {
            if (invoice.getDefaultPaymentMethod() != null) protectedCards.put(invoice.getDefaultPaymentMethod(),
                    "This card is assigned to an outstanding invoice. Resolve its payment before removing it.");
            var payment = invoice.getPaymentIntentObject();
            if (payment != null && payment.getPaymentMethod() != null
                    && Set.of("processing", "requires_action", "requires_confirmation", "requires_capture").contains(payment.getStatus())) {
                protectedCards.put(payment.getPaymentMethod(),
                        "This card has an unfinished invoice payment. Resolve its payment before removing it.");
            }
        }
        for (SubscriptionSchedule schedule : SubscriptionSchedule.list(SubscriptionScheduleListParams.builder()
                .setCustomer(customerId).setLimit(100L).build(), config.requestOptions()).autoPagingIterable()) {
            if (!Set.of("active", "not_started").contains(schedule.getStatus())) continue;
            String reason = "This card is used by a scheduled subscription. Change its renewal card before removing it.";
            if (schedule.getDefaultSettings() != null && schedule.getDefaultSettings().getDefaultPaymentMethod() != null) {
                protectedCards.put(schedule.getDefaultSettings().getDefaultPaymentMethod(), reason);
            }
            if (schedule.getPhases() != null) for (var phase : schedule.getPhases()) {
                if (phase.getDefaultPaymentMethod() != null && (phase.getEndDate() == null
                        || phase.getEndDate() > java.time.Instant.now().getEpochSecond())) {
                    protectedCards.put(phase.getDefaultPaymentMethod(), reason);
                }
            }
        }
        final String defaultCard = proDefault;
        final boolean needsLastCard = hasSubscriptionWithoutDefault && cards.size() == 1;
        return cards.stream().filter(card -> card.getCard() != null).map(card -> {
            String reason = protectedCards.get(card.getId());
            if (reason == null && needsLastCard) reason = "Add a replacement renewal card before removing your last card.";
            return new CardView(card.getId(), card.getCard().getBrand(), card.getCard().getLast4(),
                    card.getCard().getExpMonth(), card.getCard().getExpYear(),
                    card.getBillingDetails() == null ? null : card.getBillingDetails().getName(),
                    card.getId().equals(defaultCard), reason == null, reason);
        }).toList();
    }

    private PaymentMethod ownedCard(User user, String id) throws StripeException {
        if (user.getStripeCustomerId() == null) throw forbidden("There is no saved card for this account.");
        PaymentMethod card = PaymentMethod.retrieve(id, config.requestOptions());
        if (!user.getStripeCustomerId().equals(card.getCustomer()) || card.getCard() == null) {
            throw forbidden("This card does not belong to your account.");
        }
        return card;
    }

    private User lockUser(User authenticated) {
        if (authenticated == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to manage billing.");
        User user = users.findByIdForUpdate(authenticated.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again."));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (Boolean.TRUE.equals(user.getDeleted())) throw forbidden("This account is unavailable.");
        return user;
    }

    private void requireProvider() {
        if (!config.hasApiKey()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Card management is temporarily unavailable.");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
