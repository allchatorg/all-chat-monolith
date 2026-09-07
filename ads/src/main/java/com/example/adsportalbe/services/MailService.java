package com.example.adsportalbe.services;

import com.mk3.chatapp.models.identity.User;

public interface MailService {
    void sendSimpleMail(String to, String subject, String text);

    void sendResetPasswordEmail(User user, String token);

    void sendVerificationEmail(User user, String code);

    void sendEmailUpdateVerification(String newEmail, String code);

    void sendPurchaseUpdateEmail(String to, String title, String body, String purchaseReference, String detailsPath);
}
