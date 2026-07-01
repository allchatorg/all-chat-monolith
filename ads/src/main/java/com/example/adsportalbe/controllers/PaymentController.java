package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.payment.AddPaymentMethodRequestDto;
import com.example.adsportalbe.dto.payment.PaymentMethodDto;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.SecurityService;
import com.example.adsportalbe.services.PaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ads-portal/payment/methods")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    // @AuthenticationPrincipal yields null under the merged Redis session auth
    // (principal is the user-id name, not a User); resolve via chat's SecurityService.
    private final SecurityService securityService;

    @GetMapping
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods()
            throws StripeException {
        User user = securityService.getCurrentUser();
        return ResponseEntity.ok(paymentService.getPaymentMethods(user));
    }

    @PostMapping
    public ResponseEntity<Void> addPaymentMethod(@RequestBody @Valid AddPaymentMethodRequestDto request)
            throws StripeException {
        User user = securityService.getCurrentUser();
        paymentService.addPaymentMethod(user, request.getPaymentMethodId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removePaymentMethod(@PathVariable("id") String paymentMethodId)
            throws StripeException {
        User user = securityService.getCurrentUser();
        paymentService.removePaymentMethod(user, paymentMethodId);
        return ResponseEntity.ok().build();
    }
}
