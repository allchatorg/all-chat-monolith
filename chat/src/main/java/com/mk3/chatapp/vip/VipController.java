package com.mk3.chatapp.vip;

import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/vip")
@RequiredArgsConstructor
@Slf4j
public class VipController {
    private final VipBillingService service;
    private final VipBillingManagementService management;

    public enum Interval { MONTHLY, YEARLY }
    public enum PortalFlow { billing, switch_plan }
    public record CheckoutRequest(@NotNull Interval interval) { }
    public record PortalRequest(@NotNull PortalFlow flow) { }
    public record RedirectResponse(String url) { }
    public record EmbeddedCheckoutResponse(String clientSecret) { }
    public record PlanConfirmationRequest(@NotBlank @Size(max = 4096) String previewToken) { }
    public record InvoicePaymentRequest(@Pattern(regexp = "pm_[A-Za-z0-9]+") String paymentMethodId) { }

    @GetMapping("/subscription")
    public VipSubscriptionSummary subscription(@RequestParam(defaultValue = "false") boolean refresh) {
        return service.getOwnSubscription(refresh);
    }

    @PostMapping("/checkout")
    public RedirectResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return providerCall(() -> new RedirectResponse(service.createCheckout(request.interval().name())));
    }

    @PostMapping("/checkout/embedded")
    public EmbeddedCheckoutResponse embeddedCheckout(@Valid @RequestBody CheckoutRequest request) {
        return providerCall(() -> new EmbeddedCheckoutResponse(service.createEmbeddedCheckout(request.interval().name())));
    }

    @GetMapping("/invoices")
    public VipBillingManagementService.InvoicePage invoices(@RequestParam(required = false) String startingAfter) {
        return providerCall(() -> management.invoices(startingAfter));
    }

    @GetMapping(value = "/invoices/{invoiceId}/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable String invoiceId) {
        return providerCall(() -> ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Content-Disposition", "attachment; filename=allchat-invoice.pdf")
                .body(management.invoicePdf(invoiceId)));
    }

    @PostMapping("/invoices/{invoiceId}/pay")
    public VipBillingManagementService.PaymentResult payInvoice(@PathVariable String invoiceId,
            @Valid @RequestBody(required = false) InvoicePaymentRequest request) {
        return providerCall(() -> management.payInvoice(invoiceId, request == null ? null : request.paymentMethodId()));
    }

    @PostMapping("/plan-change/preview")
    public VipBillingManagementService.PlanPreview previewPlan(@Valid @RequestBody CheckoutRequest request) {
        return providerCall(() -> management.preview(request.interval().name()));
    }

    @PostMapping("/plan-change/confirm")
    public VipBillingManagementService.PaymentResult confirmPlan(@Valid @RequestBody PlanConfirmationRequest request) {
        return providerCall(() -> management.confirm(request.previewToken()));
    }

    @PostMapping("/portal")
    public RedirectResponse portal(@Valid @RequestBody PortalRequest request) {
        return providerCall(() -> new RedirectResponse(service.createPortal(request.flow() == PortalFlow.switch_plan)));
    }

    @PostMapping("/cancel")
    public VipSubscriptionSummary cancel() {
        return providerCall(service::cancel);
    }

    @PostMapping("/resume")
    public VipSubscriptionSummary resume() {
        return providerCall(service::resume);
    }

    @DeleteMapping("/scheduled-plan-change")
    public VipSubscriptionSummary undoScheduledChange() {
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
            log.warn("VIP provider operation failed (type={}, code={}, status={}, requestId={})",
                    e.getClass().getSimpleName(), e.getCode(), e.getStatusCode(), e.getRequestId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Billing is temporarily unavailable. Please try again.");
        }
    }

    @FunctionalInterface
    private interface ProviderOperation<T> { T run() throws StripeException; }
}
