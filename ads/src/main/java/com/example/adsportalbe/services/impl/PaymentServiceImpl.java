package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.dto.payment.PaymentMethodDto;
import com.example.adsportalbe.repositories.AdsUserRepository;
import com.example.adsportalbe.services.PaymentService;
import com.mk3.chatapp.models.identity.User;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final AdsUserRepository userRepository;
    @Value("${STRIPE_API_KEY}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    @Override
    public String createCustomer(User user) throws StripeException {
        // The @AuthenticationPrincipal is a snapshot serialized into the Spring
        // Session (Redis) at login time, so its stripeCustomerId is stale (usually
        // null) for the whole session. Re-read the persisted user so we reuse the
        // same Stripe customer across requests instead of creating a new one each
        // time (which would orphan saved cards and authorizations).
        User dbUser = userRepository.findById(user.getId()).orElse(user);

        if (dbUser.getStripeCustomerId() != null) {
            // Keep the in-memory principal in sync for the rest of this request.
            user.setStripeCustomerId(dbUser.getStripeCustomerId());
            return dbUser.getStripeCustomerId();
        }

        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(dbUser.getEmail())
                .setName(dbUser.getFirstName() + " " + dbUser.getLastName())
                .build();

        Customer customer = Customer.create(params);
        dbUser.setStripeCustomerId(customer.getId());
        userRepository.save(dbUser); // Persist connection

        user.setStripeCustomerId(customer.getId());
        return customer.getId();
    }

    @Override
    public List<PaymentMethodDto> getPaymentMethods(User user) throws StripeException {
        String customerId = createCustomer(user); // Ensure customer exists

        PaymentMethodListParams params = PaymentMethodListParams.builder()
                .setCustomer(customerId)
                .setType(PaymentMethodListParams.Type.CARD)
                .build();

        PaymentMethodCollection paymentMethods = PaymentMethod.list(params);

        List<PaymentMethodDto> dtos = new ArrayList<>();
        for (PaymentMethod pm : paymentMethods.getData()) {
            if (pm.getCard() != null) {
                dtos.add(PaymentMethodDto.builder()
                        .id(pm.getId())
                        .brand(pm.getCard().getBrand())
                        .last4(pm.getCard().getLast4())
                        .expMonth(pm.getCard().getExpMonth())
                        .expYear(pm.getCard().getExpYear())
                        .cardholderName(pm.getBillingDetails() != null ? pm.getBillingDetails().getName() : null)
                        .build());
            }
        }
        return dtos;
    }

    @Override
    public void addPaymentMethod(User user, String paymentMethodId) throws StripeException {
        String customerId = createCustomer(user);

        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);

        PaymentMethodAttachParams params = PaymentMethodAttachParams.builder()
                .setCustomer(customerId)
                .build();

        paymentMethod.attach(params);
    }

    @Override
    public void removePaymentMethod(User user, String paymentMethodId) throws StripeException {
        // First verify this card belongs to the user
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);

        // Basic check to ensure we are not deleting someone else's card if the ID is
        // guessed
        // (Stripe IDs are distinct, but good detailed check)
        String customerId = createCustomer(user);
        if (!customerId.equals(paymentMethod.getCustomer())) {
            throw new IllegalArgumentException("Payment method does not belong to the user");
        }

        paymentMethod.detach();
    }

    @Override
    public String authorizePayment(User user, String paymentMethodId, Long amountCents, String stripeAccount)
            throws StripeException {
        String customerId = createCustomer(user);

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd") // Assuming USD for now
                .setCustomer(customerId)
                .setPaymentMethod(paymentMethodId)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .setConfirm(true)
                .setReturnUrl("http://localhost:3000/payment-return"); // Placeholder

        if (stripeAccount != null && !stripeAccount.isEmpty() && !stripeAccount.equals("TBD_STRIPE_ACCOUNT_ID")) {
            // If there's a connected account involved, set transfer data or on_behalf_of
            // logic
            // For now, standard charge
        }

        PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());
        // Purchase confirmations must only describe a hold Stripe actually authorized.
        if (!"requires_capture".equals(paymentIntent.getStatus())) {
            throw new IllegalStateException("Payment authorization could not be completed. Please try another saved payment method.");
        }
        return paymentIntent.getId();
    }

    @Override
    public PaymentMethodDto getPaymentMethod(String paymentMethodId) throws StripeException {
        PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);

        if (pm.getCard() != null) {
            return PaymentMethodDto.builder()
                    .id(pm.getId())
                    .brand(pm.getCard().getBrand())
                    .last4(pm.getCard().getLast4())
                    .expMonth(pm.getCard().getExpMonth())
                    .expYear(pm.getCard().getExpYear())
                    .cardholderName(pm.getBillingDetails() != null ? pm.getBillingDetails().getName() : null)
                    .build();
        }
        return null;
    }

    @Override
    public void cancelPaymentAuthorization(String paymentIntentId) throws StripeException {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            throw new IllegalArgumentException("Payment intent ID cannot be null or empty");
        }

        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

        // A retry after a successful release is safe. Never report a release
        // when Stripe still has a captured or processing payment.
        if ("canceled".equals(paymentIntent.getStatus())) {
            return;
        }
        if ("requires_capture".equals(paymentIntent.getStatus()) ||
                "requires_payment_method".equals(paymentIntent.getStatus()) ||
                "requires_confirmation".equals(paymentIntent.getStatus()) ||
                "requires_action".equals(paymentIntent.getStatus())) {
            PaymentIntent canceled = paymentIntent.cancel();
            if (!"canceled".equals(canceled.getStatus())) {
                throw new IllegalStateException("Payment authorization release has not completed");
            }
            log.info("Cancelled payment intent: {}", paymentIntentId);
        } else {
            throw new IllegalStateException("Payment authorization cannot be released in its current state: "
                    + paymentIntent.getStatus());
        }
    }

    @Override
    public void capturePayment(String paymentIntentId) throws StripeException {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            throw new IllegalArgumentException("Payment intent ID cannot be null or empty");
        }

        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

        // Capture the payment if it's capturable
        if ("requires_capture".equals(paymentIntent.getStatus())) {
            PaymentIntent captured = paymentIntent.capture();
            if (!"succeeded".equals(captured.getStatus())) {
                throw new IllegalStateException("Payment capture has not completed");
            }
            log.info("Captured payment intent: {}", paymentIntentId);
        } else {
            throw new IllegalStateException(
                    "Payment intent " + paymentIntentId + " cannot be captured, current status: "
                            + paymentIntent.getStatus());
        }
    }

    @Override
    public void refundPayment(String paymentIntentId) throws StripeException {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            throw new IllegalArgumentException("Payment intent ID cannot be null or empty");
        }

        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

        // Only captured payments can be refunded
        if ("succeeded".equals(paymentIntent.getStatus())) {
            Refund refund = Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .build());
            if ("failed".equals(refund.getStatus()) || "canceled".equals(refund.getStatus())) {
                throw new IllegalStateException("The payment refund was not accepted");
            }
            log.info("Refund requested for payment intent: {}", paymentIntentId);
        } else {
            throw new IllegalStateException(
                    "Payment intent " + paymentIntentId + " cannot be refunded, current status: "
                            + paymentIntent.getStatus());
        }
    }
}
