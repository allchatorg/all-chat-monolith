package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.ResetPasswordRequestDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.ClaimAccountResponseDTO;
import com.mk3.chatapp.dtos.responses.IpDetailsResponseDTO;
import com.mk3.chatapp.dtos.responses.PhonePasswordResetVerificationResponseDTO;
import com.mk3.chatapp.dtos.responses.SessionTokenDTO;
import com.mk3.chatapp.services.AuthenticationService;
import com.mk3.chatapp.services.IpService;
import com.mk3.chatapp.utils.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.FRONT_END.URL}", allowCredentials = "true")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final IpService ipService;

    @PostMapping("/register")
    public ResponseEntity<SessionTokenDTO> register(@RequestBody RegisterRequestDTO registerDTO, HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(authenticationService.register(registerDTO, httpServletRequest));
    }

    @PostMapping("/register-guest")
    public ResponseEntity<SessionTokenDTO> registerGuest(HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(authenticationService.registerGuest(httpServletRequest));
    }

    @PostMapping("/register-unclaimed")
    public ResponseEntity<SessionTokenDTO> registerUnclaimed(@Valid @RequestBody UnclaimedRegisterDTO registerDTO, HttpServletRequest httpServletRequest) {

        return ResponseEntity.ok(authenticationService.unclaimedRegister(registerDTO, httpServletRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<SessionTokenDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO, HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(authenticationService.login(loginRequestDTO, httpServletRequest));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        authenticationService.initiatePasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password/verify-phone-code")
    public ResponseEntity<PhonePasswordResetVerificationResponseDTO> verifyPhonePasswordReset(
            @Valid @RequestBody PhonePasswordResetVerificationRequestDTO request) {
        return ResponseEntity.ok(authenticationService.verifyPhonePasswordReset(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<SessionTokenDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request, HttpServletRequest httpServletRequest) {
        var user = authenticationService.resetPassword(request);

        var token = authenticationService.login(new LoginRequestDTO(user.email(), request.newPassword()), httpServletRequest);
        return ResponseEntity.ok(token);
    }

    @PatchMapping("/claim-account")
    public ResponseEntity<ClaimAccountResponseDTO> claimAccount(@Valid @RequestBody ClaimAccountRequestDTO claimAccountRequestDTO, Principal connectedUser, HttpServletRequest request) {
        var claimAccountResponse = authenticationService.claimAccount(claimAccountRequestDTO, connectedUser, request);
        return ResponseEntity.ok(claimAccountResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, Principal connectedUser) {
        authenticationService.logout(request, connectedUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ping")
    public ResponseEntity<IpDetailsResponseDTO> ping(HttpServletRequest request) {
        String ipAddress = IpAddressUtils.getClientIpAddress(request);
        var requiredVerification = ipService.getRequiredVerification(ipAddress);
        IpDetailsResponseDTO response = new IpDetailsResponseDTO(requiredVerification);
        return ResponseEntity.ok(response);
    }
}
