package com.mk3.chatapp.pro;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ProSubscriptionRepository;
import com.mk3.chatapp.services.BillingPaymentMethodService;
import com.mk3.chatapp.services.SecurityService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pro/payment-methods")
@RequiredArgsConstructor
@Slf4j
public class ProPaymentMethodController {
    private final BillingPaymentMethodService cards;
    private final SecurityService security;
    private final ProSubscriptionRepository subscriptions;

    public record SetupRequest(@Pattern(regexp = "pm_[A-Za-z0-9]+") String paymentMethodId) { }
    public record CompleteRequest(@NotBlank @Pattern(regexp = "seti_[A-Za-z0-9]+") String setupIntentId,
                                  boolean makeDefault) { }

    @GetMapping
    public List<BillingPaymentMethodService.CardView> list() {
        return provider(() -> cards.list(owner()));
    }

    @PostMapping("/setup-intent")
    public BillingPaymentMethodService.SetupResponse setup(@Valid @RequestBody(required = false) SetupRequest request) {
        return provider(() -> cards.createSetup(owner(), request == null ? null : request.paymentMethodId()));
    }

    @PostMapping("/setup-complete")
    public List<BillingPaymentMethodService.CardView> complete(@Valid @RequestBody CompleteRequest request) {
        return provider(() -> cards.completeSetup(owner(), request.setupIntentId(), request.makeDefault()));
    }

    @PostMapping("/{id}/default")
    public List<BillingPaymentMethodService.CardView> setDefault(@PathVariable String id) {
        return provider(() -> cards.setDefault(owner(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable String id) {
        return provider(() -> { cards.remove(owner(), id); return ResponseEntity.noContent().build(); });
    }

    private User owner() {
        User user = security.getCurrentUser();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to manage billing.");
        // Maintenance routes remain available to restricted existing subscribers, without
        // allowing the billing whitelist to provision a new account or purchase a plan.
        if (user.getStripeCustomerId() == null || !subscriptions.existsById(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no Pro billing account to manage yet.");
        }
        return user;
    }

    private <T> T provider(Operation<T> operation) {
        try {
            return operation.run();
        } catch (StripeException failure) {
            log.warn("Card management failed (type={}, code={}, requestId={})",
                    failure.getClass().getSimpleName(), failure.getCode(), failure.getRequestId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Card management is temporarily unavailable. Please try again.");
        }
    }

    @FunctionalInterface
    private interface Operation<T> { T run() throws StripeException; }
}
