package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.payment.AddPaymentMethodRequestDto;
import com.example.adsportalbe.dto.payment.PaymentMethodDto;
import com.mk3.chatapp.models.identity.User;
import com.example.adsportalbe.services.PaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ads-portal/payment/methods")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods(@AuthenticationPrincipal User user)
            throws StripeException {
        return ResponseEntity.ok(paymentService.getPaymentMethods(user));
    }

    @PostMapping
    public ResponseEntity<Void> addPaymentMethod(@AuthenticationPrincipal User user,
                                                 @RequestBody @Valid AddPaymentMethodRequestDto request) throws StripeException {
        paymentService.addPaymentMethod(user, request.getPaymentMethodId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removePaymentMethod(@AuthenticationPrincipal User user,
                                                    @PathVariable("id") String paymentMethodId) throws StripeException {
        paymentService.removePaymentMethod(user, paymentMethodId);
        return ResponseEntity.ok().build();
    }
}
