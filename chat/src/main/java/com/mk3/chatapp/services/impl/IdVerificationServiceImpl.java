package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.IdVerificationSessionResponseDTO;
import com.mk3.chatapp.dtos.responses.IdVerificationStatusResponseDTO;
import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.events.IdVerificationRequiredEvent;
import com.mk3.chatapp.events.IdVerificationResultEvent;
import com.mk3.chatapp.exceptions.ConflictException;
import com.mk3.chatapp.models.AuditLog;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.*;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.identity.VerificationSession;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.identity.VerificationSessionCreateParams;
import com.stripe.param.identity.VerificationSessionRetrieveParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdVerificationServiceImpl implements IdVerificationService {

    private static final String VERIFIED_EVENT = "identity.verification_session.verified";
    private static final String REQUIRES_INPUT_EVENT = "identity.verification_session.requires_input";
    private static final String USER_ID_METADATA_KEY = "user_id";
    private static final int ADULT_AGE = 18;

    private final UserService userService;
    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final AuditLogService auditLogService;
    private final ReportCaseService reportCaseService;
    private final MailSenderService mailSenderService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.identity.webhook-secret}")
    private String webhookSecret;

    @Value("${app.admin.email:admin@allchat.org}")
    private String adminEmail;

    @Override
    @Transactional
    public void requireIdVerification(Long targetUserId, Long reportCaseId) {
        User targetUser = userService.findById(targetUserId);

        if (targetUser.getIdVerificationStatus() == IdVerificationStatus.VERIFIED) {
            throw new ConflictException("User has already passed identity verification");
        }

        if (reportCaseId != null) {
            // Validate the report case exists before linking it to the user.
            reportCaseService.findById(reportCaseId);
        }

        targetUser.setIdVerificationStatus(IdVerificationStatus.REQUIRED);
        targetUser.setIdVerificationSessionId(null);
        targetUser.setIdVerificationReportCaseId(reportCaseId);
        userService.save(targetUser);

        AuditLog auditLog = auditLogService.logRequireIdVerification(
                "REQUIRE_ID_VERIFICATION",
                "User was required to complete identity verification",
                targetUserId,
                reportCaseId);
        if (reportCaseId != null) {
            addLogToReportCase(reportCaseId, auditLog);
        }

        eventPublisher.publishEvent(new IdVerificationRequiredEvent(targetUserId, reportCaseId));
    }

    @Override
    @Transactional
    public void clearIdVerificationRequirement(Long targetUserId) {
        User targetUser = userService.findById(targetUserId);

        if (targetUser.getIdVerificationStatus() == IdVerificationStatus.VERIFIED) {
            throw new ConflictException("User has already passed identity verification; there is no requirement to clear");
        }

        Long reportCaseId = targetUser.getIdVerificationReportCaseId();
        targetUser.setIdVerificationStatus(IdVerificationStatus.NONE);
        targetUser.setIdVerificationSessionId(null);
        targetUser.setIdVerificationReportCaseId(null);
        userService.save(targetUser);

        AuditLog auditLog = auditLogService.logClearIdVerification(
                "CLEAR_ID_VERIFICATION",
                "The identity verification requirement was cleared for the user",
                targetUserId,
                reportCaseId);
        if (reportCaseId != null) {
            addLogToReportCase(reportCaseId, auditLog);
        }
    }

    @Override
    @Transactional
    public IdVerificationSessionResponseDTO createVerificationSession() {
        User user = securityService.getCurrentUser();
        IdVerificationStatus status = user.getIdVerificationStatus();

        if (status != IdVerificationStatus.REQUIRED && status != IdVerificationStatus.REJECTED) {
            throw new ConflictException("Identity verification session can only be started when verification is required");
        }

        if (status == IdVerificationStatus.REJECTED && user.getVerifiedDateOfBirth() != null) {
            throw new ConflictException("You must be 18 or older. Access will be granted automatically once you turn 18.");
        }

        VerificationSessionCreateParams params = VerificationSessionCreateParams.builder()
                .setType(VerificationSessionCreateParams.Type.DOCUMENT)
                .putMetadata(USER_ID_METADATA_KEY, user.getId().toString())
                .setOptions(VerificationSessionCreateParams.Options.builder()
                        .setDocument(VerificationSessionCreateParams.Options.Document.builder()
                                .setRequireMatchingSelfie(true)
                                .build())
                        .build())
                .build();

        VerificationSession session;
        try {
            session = VerificationSession.create(params, requestOptions());
        } catch (StripeException e) {
            log.error("Failed to create Stripe identity verification session for user {}", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to create identity verification session");
        }

        user.setIdVerificationSessionId(session.getId());
        user.setIdVerificationStatus(IdVerificationStatus.PENDING);
        userService.save(user);

        return new IdVerificationSessionResponseDTO(session.getClientSecret());
    }

    @Override
    public IdVerificationStatusResponseDTO getOwnStatus() {
        User user = securityService.getCurrentUser();
        IdVerificationStatus status = user.getIdVerificationStatus() == null
                ? IdVerificationStatus.NONE
                : user.getIdVerificationStatus();
        return new IdVerificationStatusResponseDTO(status);
    }

    @Override
    @Transactional
    public void handleWebhookEvent(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe signature");
        }

        switch (event.getType()) {
            case VERIFIED_EVENT -> handleSessionVerified(event);
            case REQUIRES_INPUT_EVENT -> handleSessionRequiresInput(event);
            default -> log.debug("Ignoring unhandled Stripe identity event type {}", event.getType());
        }
    }

    @Override
    @Transactional
    public void promoteEligibleUnderageUsers() {
        LocalDate cutoff = LocalDate.now().minusYears(ADULT_AGE);
        List<User> eligible = userRepository.findByIdVerificationStatusAndVerifiedDateOfBirthLessThanEqual(
                IdVerificationStatus.REJECTED, cutoff);
        for (User user : eligible) {
            log.info("Auto-promoting user {}: previously rejected as underage, now of age", user.getId());
            passVerification(user, user.getIdVerificationReportCaseId());
        }
    }

    private void handleSessionVerified(Event event) {
        VerificationSession eventSession = extractSession(event);

        VerificationSession session;
        try {
            session = VerificationSession.retrieve(
                    eventSession.getId(),
                    VerificationSessionRetrieveParams.builder().addExpand("verified_outputs.dob").build(),
                    requestOptions());
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe identity verification session {}", eventSession.getId(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to retrieve identity verification session");
        }

        User user = resolveUser(session);
        if (user == null) {
            log.warn("No user found for Stripe identity verification session {}", session.getId());
            return;
        }

        var verifiedOutputs = session.getVerifiedOutputs();
        var dob = verifiedOutputs != null ? verifiedOutputs.getDob() : null;
        if (dob == null || dob.getYear() == null || dob.getMonth() == null || dob.getDay() == null) {
            log.warn("Stripe identity verification session {} has no verified date of birth", session.getId());
            return;
        }

        LocalDate birthDate = LocalDate.of(dob.getYear().intValue(), dob.getMonth().intValue(), dob.getDay().intValue());
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        Long reportCaseId = user.getIdVerificationReportCaseId();
        user.setVerifiedDateOfBirth(birthDate);

        if (age >= ADULT_AGE) {
            passVerification(user, reportCaseId);
        } else {
            failVerification(user, reportCaseId);
        }
    }

    private void passVerification(User user, Long reportCaseId) {
        user.setIdVerificationStatus(IdVerificationStatus.VERIFIED);
        user.setOver18(true);
        userService.save(user);

        AuditLog auditLog = auditLogService.logIdVerificationPassed(
                "ID_VERIFICATION_PASSED",
                "User passed identity verification (18 or older)",
                user.getId(),
                reportCaseId);
        if (reportCaseId != null) {
            addLogToReportCase(reportCaseId, auditLog);
        }

        notifyResult(user, reportCaseId, true);
    }

    private void failVerification(User user, Long reportCaseId) {
        user.setIdVerificationStatus(IdVerificationStatus.REJECTED);
        userService.save(user);

        AuditLog auditLog = auditLogService.logIdVerificationFailed(
                "ID_VERIFICATION_FAILED",
                "User failed identity verification (under 18)",
                user.getId(),
                reportCaseId);
        if (reportCaseId != null) {
            addLogToReportCase(reportCaseId, auditLog);
            reportCaseService.requestElevation(reportCaseId);
        }

        try {
            mailSenderService.sendSimpleMail(
                    adminEmail,
                    "Identity verification failed - underage user " + user.getApplicationUsername(),
                    "User " + user.getApplicationUsername() + " (id " + user.getId()
                            + ") completed identity verification and was determined to be under 18."
                            + (reportCaseId != null ? " Linked report case: " + reportCaseId + "." : ""));
        } catch (Exception e) {
            log.error("Failed to send underage identity verification email for user {}", user.getId(), e);
        }

        notifyResult(user, reportCaseId, false);
    }

    private void handleSessionRequiresInput(Event event) {
        VerificationSession session = extractSession(event);
        User user = resolveUser(session);
        if (user == null) {
            log.warn("No user found for Stripe identity verification session {}", session.getId());
            return;
        }

        if (user.getIdVerificationStatus() == IdVerificationStatus.PENDING) {
            user.setIdVerificationStatus(IdVerificationStatus.REQUIRED);
            userService.save(user);
        }
    }

    private void notifyResult(User user, Long reportCaseId, boolean passed) {
        eventPublisher.publishEvent(new IdVerificationResultEvent(user.getId(), reportCaseId, passed));
    }

    private User resolveUser(VerificationSession session) {
        String metadataUserId = session.getMetadata() != null
                ? session.getMetadata().get(USER_ID_METADATA_KEY)
                : null;

        if (metadataUserId != null) {
            try {
                var user = userRepository.findById(Long.parseLong(metadataUserId)).orElse(null);
                if (user != null) {
                    return user;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid user_id metadata '{}' on Stripe identity verification session {}",
                        metadataUserId, session.getId());
            }
        }

        return userRepository.findByIdVerificationSessionId(session.getId()).orElse(null);
    }

    private VerificationSession extractSession(Event event) {
        var deserializer = event.getDataObjectDeserializer();
        var stripeObject = deserializer.getObject();
        if (stripeObject.isPresent()) {
            return (VerificationSession) stripeObject.get();
        }

        try {
            return (VerificationSession) deserializer.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to deserialize Stripe event payload");
        }
    }

    private void addLogToReportCase(Long reportCaseId, AuditLog auditLog) {
        ReportCase reportCase = reportCaseService.findById(reportCaseId);
        var logs = reportCase.getAuditLogs();
        if (logs == null) {
            logs = new ArrayList<>();
        }
        logs.add(auditLog);
        reportCase.setAuditLogs(logs);
        reportCaseService.save(reportCase);
    }

    private RequestOptions requestOptions() {
        return RequestOptions.builder().setApiKey(stripeSecretKey).build();
    }
}
