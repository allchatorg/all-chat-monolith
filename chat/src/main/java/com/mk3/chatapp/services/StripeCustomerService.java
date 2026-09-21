package com.mk3.chatapp.services;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Shares the same Stripe customer between chat subscriptions and advertising. */
@Service
@RequiredArgsConstructor
public class StripeCustomerService {
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    @Value("${stripe.api-key:}")
    private String apiKey;

    @Transactional
    public String getOrCreate(User user) throws StripeException {
        User persisted = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Account no longer exists"));
        entityManager.refresh(persisted, LockModeType.PESSIMISTIC_WRITE);
        if (persisted.getStripeCustomerId() != null && !persisted.getStripeCustomerId().isBlank()) {
            user.setStripeCustomerId(persisted.getStripeCustomerId());
            return persisted.getStripeCustomerId();
        }
        var params = CustomerCreateParams.builder()
                .setName(persisted.getApplicationUsername())
                .putMetadata("allchat_user_id", persisted.getId().toString());
        if (persisted.getEmail() != null && !persisted.getEmail().isBlank()) {
            params.setEmail(persisted.getEmail());
        }
        Customer customer = Customer.create(params.build(), RequestOptions.builder()
                .setApiKey(apiKey).setConnectTimeout(10_000).setReadTimeout(20_000)
                .setMaxNetworkRetries(1)
                .setIdempotencyKey("allchat-customer-" + persisted.getId()).build());
        persisted.setStripeCustomerId(customer.getId());
        userRepository.updateStripeCustomerId(persisted.getId(), customer.getId());
        user.setStripeCustomerId(customer.getId());
        return customer.getId();
    }
}
