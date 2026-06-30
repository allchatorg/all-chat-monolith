package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.ResetPasswordRequestDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.ClaimAccountResponseDTO;
import com.mk3.chatapp.dtos.responses.PhonePasswordResetVerificationResponseDTO;
import com.mk3.chatapp.dtos.responses.SessionTokenDTO;
import com.mk3.chatapp.dtos.responses.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.security.Principal;

public interface AuthenticationService {
    SessionTokenDTO register(RegisterRequestDTO registerRequestDTO, HttpServletRequest httpServletRequest);

    SessionTokenDTO unclaimedRegister(UnclaimedRegisterDTO unclaimedRegisterDTO, HttpServletRequest httpServletRequest);

    SessionTokenDTO login(LoginRequestDTO loginRequestDTO, HttpServletRequest httpServletRequest);

    void initiatePasswordReset(@Valid ForgotPasswordRequestDTO request);

    PhonePasswordResetVerificationResponseDTO verifyPhonePasswordReset(@Valid PhonePasswordResetVerificationRequestDTO request);

    UserDTO resetPassword(@Valid ResetPasswordRequestDTO request);

    ClaimAccountResponseDTO claimAccount(@Valid ClaimAccountRequestDTO claimAccountRequestDTO, Principal connectedUser, HttpServletRequest request);

    void logout(HttpServletRequest request, Principal connectedUser);

    SessionTokenDTO registerGuest(HttpServletRequest httpServletRequest);
}
