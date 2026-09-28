package com.mk3.chatapp.services;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ProSubscriptionRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Shares the same Stripe customer between chat subscriptions and advertising. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeCustomerService {
    private final UserRepository userRepository;
    private final ProSubscriptionRepository proSubscriptions;
    private final EntityManager entityManager;
    @Value("${stripe.api-key:}")
    private String apiKey;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String getOrCreate(User user) throws StripeException {
        User persisted = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Account no longer exists"));
        entityManager.refresh(persisted, LockModeType.PESSIMISTIC_WRITE);
        String previousCustomerId = persisted.getStripeCustomerId();
        boolean hasCustomer = previousCustomerId != null && !previousCustomerId.isBlank();
        if (hasCustomer && !isDeletedTestCustomer(previousCustomerId)) {
            user.setStripeCustomerId(previousCustomerId);
            return previousCustomerId;
        }
        if (hasCustomer) requireNoSavedSubscription(persisted);
        var params = CustomerCreateParams.builder()
                .setName(persisted.getApplicationUsername())
                .putMetadata("allchat_user_id", persisted.getId().toString());
        if (persisted.getEmail() != null && !persisted.getEmail().isBlank()) {
            params.setEmail(persisted.getEmail());
        }
        // A reset may leave the original create response cached under its idempotency key.
        // Use a stable replacement key so retries cannot return the deleted customer.
        String idempotencyKey = "allchat-customer-" + persisted.getId()
                + (hasCustomer ? "-replaces-" + previousCustomerId : "");
        Customer customer = Customer.create(params.build(), requestOptions(idempotencyKey));
        persisted.setStripeCustomerId(customer.getId());
        userRepository.updateStripeCustomerId(persisted.getId(), customer.getId());
        user.setStripeCustomerId(customer.getId());
        if (hasCustomer) log.info("Replaced deleted Stripe test customer for user {}", persisted.getId());
        return customer.getId();
    }

    private boolean isDeletedTestCustomer(String customerId) throws StripeException {
        // Never replace live billing identities automatically after a key/account configuration change.
        if (apiKey == null || !(apiKey.startsWith("sk_test_") || apiKey.startsWith("rk_test_"))) return false;
        try {
            return Boolean.TRUE.equals(Customer.retrieve(customerId, requestOptions(null)).getDeleted());
        } catch (InvalidRequestException e) {
            if (Integer.valueOf(404).equals(e.getStatusCode()) && "resource_missing".equals(e.getCode())) return true;
            throw e;
        }
    }

    private void requireNoSavedSubscription(User user) {
        // The customer is also shared with ads. Do not detach saved Pro billing state
        // when an ads request encounters a deleted test customer first.
        boolean savedSubscription = proSubscriptions.findById(user.getId())
                .map(subscription -> !"NONE".equals(subscription.getStatus())
                        || subscription.getStripeSubscriptionId() != null
                        || subscription.getStripeScheduleId() != null
                        || subscription.getCheckoutSessionId() != null
                        || subscription.getCheckoutAttemptId() != null
                        || subscription.getCheckoutInterval() != null
                        || subscription.getCheckoutExpiresAt() != null
                        || subscription.getPaidThrough() != null
                        || subscription.getCurrentPeriodEnd() != null
                        || subscription.getBillingInterval() != null
                        || subscription.getScheduledInterval() != null
                        || subscription.getScheduledChangeAt() != null
                        || subscription.isCancelAtPeriodEnd())
                .orElse(false);
        if (savedSubscription || user.getProPaidThrough() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your Stripe test customer was deleted. Clear its saved test subscription state before trying again.");
        }
    }

    private RequestOptions requestOptions(String idempotencyKey) {
        return RequestOptions.builder().setApiKey(apiKey).setConnectTimeout(10_000).setReadTimeout(20_000)
                .setMaxNetworkRetries(1).setIdempotencyKey(idempotencyKey).build();
    }
}
