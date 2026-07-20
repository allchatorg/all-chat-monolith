package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.payment.PaymentMethodDto;
import com.mk3.chatapp.models.identity.User;
import com.stripe.exception.StripeException;

import java.util.List;

public interface PaymentService {
    String createCustomer(User user) throws StripeException;

    List<PaymentMethodDto> getPaymentMethods(User user) throws StripeException;

    void addPaymentMethod(User user, String paymentMethodId) throws StripeException;

    void removePaymentMethod(User user, String paymentMethodId) throws StripeException;

    String authorizePayment(User user, String paymentMethodId, Long amountCents, String stripeAccount)
            throws StripeException;

    PaymentMethodDto getPaymentMethod(String paymentMethodId) throws StripeException;

    void cancelPaymentAuthorization(String paymentIntentId) throws StripeException;

    void capturePayment(String paymentIntentId) throws StripeException;

    void refundPayment(String paymentIntentId) throws StripeException;
}
