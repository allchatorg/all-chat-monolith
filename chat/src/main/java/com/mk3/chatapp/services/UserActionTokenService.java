package com.mk3.chatapp.services;


import com.mk3.chatapp.models.UserActionToken;
import com.mk3.chatapp.models.identity.User;

public interface UserActionTokenService {
    UserActionToken createPasswordResetTokenForUser(User user);

    UserActionToken createEmailVerificationTokenForUser(User user);

    UserActionToken findToken(String token);

    UserActionToken save(UserActionToken userActionToken);

    UserActionToken createPhoneVerificationToken(User currentUser, String phoneNumber);

    UserActionToken createEmailUpdateTokenForUser(User user, String newEmail);

    UserActionToken createPhonePasswordResetTokenForUser(User user, String phoneNumber);
}
