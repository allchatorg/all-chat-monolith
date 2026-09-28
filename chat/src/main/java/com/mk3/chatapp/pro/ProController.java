package com.mk3.chatapp.pro;

import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/pro")
@RequiredArgsConstructor
@Slf4j
public class ProController {
    private final ProBillingService service;

    public enum Interval { MONTHLY, YEARLY }
    public enum PortalFlow { billing, switch_plan }
    public record CheckoutRequest(@NotNull Interval interval) { }
    public record PortalRequest(@NotNull PortalFlow flow) { }
    public record RedirectResponse(String url) { }

    @GetMapping("/subscription")
    public ProSubscriptionSummary subscription(@RequestParam(defaultValue = "false") boolean refresh) {
        return service.getOwnSubscription(refresh);
    }

    @PostMapping("/checkout")
    public RedirectResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return providerCall(() -> new RedirectResponse(service.createCheckout(request.interval().name())));
    }

    @PostMapping("/portal")
    public RedirectResponse portal(@Valid @RequestBody PortalRequest request) {
        return providerCall(() -> new RedirectResponse(service.createPortal(request.flow() == PortalFlow.switch_plan)));
    }

    @PostMapping("/cancel")
    public ProSubscriptionSummary cancel() {
        return providerCall(service::cancel);
    }

    @PostMapping("/resume")
    public ProSubscriptionSummary resume() {
        return providerCall(service::resume);
    }

    @DeleteMapping("/scheduled-plan-change")
    public ProSubscriptionSummary undoScheduledChange() {
        return providerCall(service::undoScheduledChange);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        providerCall(() -> { service.handleWebhook(payload, signature); return null; });
        return ResponseEntity.ok().build();
    }

    private <T> T providerCall(ProviderOperation<T> operation) {
        try {
            return operation.run();
        } catch (StripeException e) {
            // Do not surface/log raw provider responses containing billing details.
            log.warn("Pro provider operation failed (type={}, code={}, status={}, requestId={})",
                    e.getClass().getSimpleName(), e.getCode(), e.getStatusCode(), e.getRequestId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Billing is temporarily unavailable. Please try again.");
        }
    }

    @FunctionalInterface
    private interface ProviderOperation<T> { T run() throws StripeException; }
}
