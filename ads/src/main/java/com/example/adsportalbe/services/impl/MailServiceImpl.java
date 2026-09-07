package com.example.adsportalbe.services.impl;

import com.mk3.chatapp.models.identity.User;
import com.example.adsportalbe.services.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    private final String FROM_NAME = "allchat Team";

    @Value("${app.FRONT_END.URL}")
    private String FRONT_END_URL;

    @Value("${SPRING_MAIL_USERNAME}")
    private String MAIL_USERNAME;

    @Override
    public void sendSimpleMail(String to, String subject, String text) {
        log.info("Sending simple email to {}", to);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        message.setFrom(MAIL_USERNAME);

        mailSender.send(message);

        log.info("Simple email sent to {}", to);
    }

    @Override
    public void sendResetPasswordEmail(User user, String token) {
        log.info("Sending reset password email to {}", user.getEmail());

        String resetLink = FRONT_END_URL + "/reset-password?token=" + token;

        Context context = new Context();
        context.setVariable("name", user.getFirstName());
        context.setVariable("resetLink", resetLink);

        String htmlContent = templateEngine.process("ads-portal/RESET_PASSWORD_TEMPLATE", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setTo(user.getEmail());
            helper.setSubject("Password Reset");
            helper.setText(htmlContent, true);
            helper.setFrom(MAIL_USERNAME, FROM_NAME);

            mailSender.send(message);

            log.info("Reset password email sent to {}", user.getEmail());
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send reset password email to {}", user.getEmail(), e);
            throw new RuntimeException("Failed to send reset password email", e);
        }
    }

    @Override
    public void sendVerificationEmail(User user, String code) {
        log.info("Sending verification email to {}", user.getEmail());

        Context context = new Context();
        context.setVariable("name", user.getFirstName());
        context.setVariable("verificationCode", code);

        String htmlContent = templateEngine.process("ads-portal/EMAIL_VERIFICATION_TEMPLATE", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setTo(user.getEmail());
            helper.setSubject("Email Verification");
            helper.setText(htmlContent, true);
            helper.setFrom(MAIL_USERNAME, FROM_NAME);

            mailSender.send(message);

            log.info("Verification email sent to {}", user.getEmail());
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send verification email to {}", user.getEmail(), e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    @Override
    public void sendEmailUpdateVerification(String newEmail, String code) {
        log.info("Sending email update verification to {}", newEmail);

        Context context = new Context();
        context.setVariable("verificationCode", code);
        context.setVariable("newEmail", newEmail);

        String htmlContent = templateEngine.process("ads-portal/EMAIL_UPDATE_TEMPLATE", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

            helper.setTo(newEmail);
            helper.setSubject("Email Update Verification");
            helper.setText(htmlContent, true);
            helper.setFrom(MAIL_USERNAME, FROM_NAME);

            mailSender.send(message);

            log.info("Email update verification sent to {}", newEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email update verification to {}", newEmail, e);
            throw new RuntimeException("Failed to send email update verification", e);
        }
    }

    @Override
    public void sendPurchaseUpdateEmail(String to, String title, String body, String purchaseReference,
                                        String detailsPath) {
        String detailsUrl = FRONT_END_URL.replaceAll("/+$", "") + detailsPath;
        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("paragraphs", body.split("\\R\\s*\\R"));
        context.setVariable("purchaseReference", purchaseReference);
        context.setVariable("detailsUrl", detailsUrl);
        String htmlContent = templateEngine.process("ads-portal/PURCHASE_UPDATE_TEMPLATE", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(title);
            helper.setFrom(MAIL_USERNAME, FROM_NAME);
            helper.setText(purchaseReference + "\n\n" + body + "\n\nView purchase: " + detailsUrl, htmlContent);
            helper.addInline("allchat-light-logo", new ClassPathResource("mail/allchat-light-logo.png"), "image/png");
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            // Only the outbox worker logs a safe failure category and retries after purchase commit.
            throw new org.springframework.mail.MailPreparationException("Could not prepare purchase update email", exception);
        }
    }
}
