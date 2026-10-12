package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.ModeratorApplicationRequest;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.MailSenderService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
public class MailSenderServiceImpl implements MailSenderService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String FROM_NAME = "allchat Team";
    @Value("${app.FRONT_END.URL}")
    private String FRONT_END_URL;
    @Value("${SPRING_MAIL_USERNAME}")
    private String MAIL_USERNAME;
    @Value("${app.admin.email:admin@allchat.org}")
    private String ADMIN_EMAIL;

    @Override
    public void sendSimpleMail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        message.setFrom(MAIL_USERNAME);
        mailSender.send(message);
    }

    @Override
    public void sendResetPasswordEmail(User user, String token) {
        String resetLink = FRONT_END_URL + "/reset-password?token=" + token;

        Context context = new Context();
        context.setVariable("name", user.getApplicationUsername());
        context.setVariable("resetLink", resetLink);

        sendTemplatedMail(user.getEmail(), "Password Reset", "RESET_PASSWORD_TEMPLATE", context,
                "Failed to send reset password email");
    }

    @Override
    public void sendVerificationEmail(User user, String code) {
        Context context = new Context();
        context.setVariable("name", user.getApplicationUsername());
        context.setVariable("verificationCode", code);

        sendTemplatedMail(user.getEmail(), "Email Verification", "EMAIL_VERIFICATION_TEMPLATE", context,
                "Failed to send verification email");
    }

    @Override
    public void sendEmailUpdateVerification(User user, String newEmail, String code) {
        Context context = new Context();
        context.setVariable("name", user.getApplicationUsername());
        context.setVariable("verificationCode", code);
        context.setVariable("settingsLink", FRONT_END_URL);

        sendTemplatedMail(newEmail, "Confirm Your New Email", "EMAIL_UPDATE_TEMPLATE", context,
                "Failed to send email update verification");
    }

    @Override
    public void sendBanAppealReceivedEmail(User user) {
        if (user.getEmail() == null) {
            return;
        }

        Context context = new Context();
        context.setVariable("name", user.getApplicationUsername());

        sendTemplatedMail(user.getEmail(), "We received your ban appeal", "BAN_APPEAL_RECEIVED_TEMPLATE", context,
                "Failed to send ban appeal received email");
    }

    @Override
    public void sendBanAppealDecisionEmail(User user, boolean approved, String userFacingMessage) {
        if (user.getEmail() == null) {
            return;
        }

        Context context = new Context();
        context.setVariable("name", user.getApplicationUsername());
        context.setVariable("approved", approved);
        context.setVariable("staffMessage", userFacingMessage);

        sendTemplatedMail(user.getEmail(), "Update on your ban appeal", "BAN_APPEAL_DECISION_TEMPLATE", context,
                "Failed to send ban appeal decision email");
    }

    @Override
    public void sendIdVerificationRequiredEmail(User user) {
        if (user.getEmail() == null) {
            return;
        }

        Context context = new Context();
        context.setVariable("name", user.getApplicationUsername());
        context.setVariable("verificationLink", FRONT_END_URL);

        sendTemplatedMail(user.getEmail(), "Identity verification required", "ID_VERIFICATION_REQUIRED_TEMPLATE", context,
                "Failed to send identity verification required email");
    }

    @Override
    public void sendModeratorApplicationEmail(User user,
                                              ModeratorApplicationRequest request) {
        Context context = new Context();
        context.setVariable("username", user.getApplicationUsername());
        context.setVariable("email", user.getEmail());
        // Standardize formatting or just pass instant.toString() for simplicity
        context.setVariable("applicationDate", java.time.Instant.now().toString());

        context.setVariable("formUsername", request.getUsername());
        context.setVariable("formEmail", request.getEmail());
        context.setVariable("timeZone", request.getTimeZone());
        context.setVariable("country", request.getCountry());
        context.setVariable("hoursPerWeek", request.getHoursPerWeek());
        context.setVariable("daysAvailable", request.getDaysAvailable() != null ? String.join(", ", request.getDaysAvailable()) : "N/A");
        context.setVariable("hasModeratedBefore", request.getHasModeratedBefore());
        context.setVariable("previousPlatforms", request.getPreviousPlatforms());
        context.setVariable("whyInterested", request.getWhyInterested());
        context.setVariable("moderatorRoleDefinition", request.getModeratorRoleDefinition());
        context.setVariable("freedomBalance", request.getFreedomBalance());
        context.setVariable("moreDangerous", request.getMoreDangerous());
        context.setVariable("comfortableWithDisturbingContent", request.getComfortableWithDisturbingContent());
        context.setVariable("unsureClassificationHandling", request.getUnsureClassificationHandling());
        context.setVariable("readTosAndGuidelines", request.getReadTosAndGuidelines());
        context.setVariable("understandNoDownload", request.getUnderstandNoDownload());
        context.setVariable("willingToFollowProcedures", request.getWillingToFollowProcedures());
        context.setVariable("agreeToKeepConfidential", request.getAgreeToKeepConfidential());
        context.setVariable("agreeNotToUseForPersonal", request.getAgreeNotToUseForPersonal());
        context.setVariable("comfortableEnforcingAgainstAgree", request.getComfortableEnforcingAgainstAgree());
        context.setVariable("howToRespondToAdminOverrule", request.getHowToRespondToAdminOverrule());
        context.setVariable("interestedInAdmin", request.getInterestedInAdmin());
        context.setVariable("whyInterestedInAdmin", request.getWhyInterestedInAdmin());
        context.setVariable("agreeToColoredName", request.getAgreeToColoredName());
        context.setVariable("confirmVolunteer", request.getConfirmVolunteer());
        context.setVariable("confirmReview", request.getConfirmReview());
        context.setVariable("confirmRemoval", request.getConfirmRemoval());
        context.setVariable("confirmUnpaid", request.getConfirmUnpaid());
        context.setVariable("signature", request.getSignature());
        context.setVariable("formDate", request.getDate());

        sendTemplatedMail(ADMIN_EMAIL, "New Moderator Application - " + user.getApplicationUsername(),
                "MODERATOR_APPLICATION_TEMPLATE", context, "Failed to send moderator application email");
    }

    private void sendTemplatedMail(String to, String subject, String template, Context context, String failureMessage) {
        String htmlContent = templateEngine.process(template, context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(MAIL_USERNAME, FROM_NAME);
            // Referenced as cid:allchat-light-logo by templates/mail/layout.html
            helper.addInline("allchat-light-logo", new ClassPathResource("mail/allchat-light-logo.png"), "image/png");

            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException(failureMessage, e);
        }
    }
}
