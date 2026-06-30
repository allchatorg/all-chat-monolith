package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.enums.TokenType;
import com.mk3.chatapp.exceptions.ConflictException;
import com.mk3.chatapp.models.UserActionToken;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserActionTokenRepository;
import com.mk3.chatapp.services.UserActionTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserActionTokenServiceImpl implements UserActionTokenService {
    private static final long PASSWORD_RESET_TOKEN_EXPIRATION = 60L * 60L; // 60 minutes in seconds
    private static final long EMAIL_VERIFICATION_TOKEN_EXPIRATION = 60L * 15L; // 15 minutes in seconds
    private static final long PHONE_VERIFICATION_TOKEN_EXPIRATION = 60L * 5L; // 5 minutes in seconds
    private static final long EMAIL_UPDATE_TOKEN_EXPIRATION = 60L * 15L; // 15 minutes in seconds
    private static final long PHONE_PASSWORD_RESET_TOKEN_EXPIRATION = 60L * 5L; // 5 minutes in seconds
    private final UserActionTokenRepository userActionTokenRepository;

    @Override
    public UserActionToken createPasswordResetTokenForUser(User user) {
        UserActionToken token = UserActionToken.builder()
                .user(user)
                .email(user.getEmail())
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusSeconds(PASSWORD_RESET_TOKEN_EXPIRATION))
                .type(TokenType.PASSWORD_RESET)
                .build();
        return save(token);
    }

    @Override
    public UserActionToken createEmailVerificationTokenForUser(User user) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new IllegalArgumentException("User email cannot be null or empty for email verification token.");
        }
        if (user.isVerified()) {
            throw new IllegalStateException("User is already verified.");
        }

        var sixDigitToken = generateSixDigitToken();


        UserActionToken token = UserActionToken.builder()
                .user(user)
                .email(user.getEmail())
                .token(sixDigitToken)
                .expiryDate(Instant.now().plusSeconds(EMAIL_VERIFICATION_TOKEN_EXPIRATION))
                .type(TokenType.EMAIL_VERIFICATION)
                .build();

        return save(token);
    }

    @Override
    public UserActionToken findToken(String token) {
        return userActionTokenRepository.findByToken(token).orElseThrow(
                () -> new ConflictException("Incorrect verification code: " + token)
        );
    }

    public UserActionToken save(UserActionToken userActionToken) {
        return userActionTokenRepository.save(userActionToken);
    }

    @Override
    public UserActionToken createPhoneVerificationToken(User currentUser, String number) {
        var sixDigitToken = generateSixDigitToken();

        UserActionToken token = UserActionToken.builder()
                .user(currentUser)
                .email(currentUser.getEmail())
                .token(sixDigitToken)
                .expiryDate(Instant.now().plusSeconds(PHONE_VERIFICATION_TOKEN_EXPIRATION))
                .type(TokenType.PHONE_VERIFICATION)
                .phoneNumber(number)
                .build();

        return save(token);
    }

    @Override
    public UserActionToken createEmailUpdateTokenForUser(User user, String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("New email cannot be null or empty.");
        }

        var sixDigitToken = generateSixDigitToken();

        UserActionToken token = UserActionToken.builder()
                .user(user)
                .email(newEmail)
                .token(sixDigitToken)
                .expiryDate(Instant.now().plusSeconds(EMAIL_UPDATE_TOKEN_EXPIRATION))
                .type(TokenType.EMAIL_UPDATE)
                .build();

        return save(token);
    }

    @Override
    public UserActionToken createPhonePasswordResetTokenForUser(User user, String phoneNumber) {
        var sixDigitToken = generateSixDigitToken();

        UserActionToken token = UserActionToken.builder()
                .user(user)
                .email(user.getEmail())
                .phoneNumber(phoneNumber)
                .token(sixDigitToken)
                .expiryDate(Instant.now().plusSeconds(PHONE_PASSWORD_RESET_TOKEN_EXPIRATION))
                .type(TokenType.PHONE_PASSWORD_RESET)
                .build();

        return save(token);
    }

    private String generateSixDigitToken() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }
}
