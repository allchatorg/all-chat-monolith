package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.ModeratorApplicationRequest;
import com.mk3.chatapp.models.identity.User;

public interface MailSenderService {
    void sendSimpleMail(String to, String subject, String text);

    void sendResetPasswordEmail(User user, String token);

    void sendVerificationEmail(User user, String code);

    void sendEmailUpdateVerification(User user, String newEmail, String code);

    void sendModeratorApplicationEmail(User user, ModeratorApplicationRequest request);

    void sendBanAppealReceivedEmail(User user);

    void sendBanAppealDecisionEmail(User user, boolean approved, String userFacingMessage);

    void sendIdVerificationRequiredEmail(User user);
}
