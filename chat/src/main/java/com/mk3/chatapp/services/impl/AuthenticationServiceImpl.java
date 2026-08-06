package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.ResetPasswordRequestDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.ClaimAccountResponseDTO;
import com.mk3.chatapp.dtos.responses.PhonePasswordResetVerificationResponseDTO;
import com.mk3.chatapp.dtos.responses.SessionTokenDTO;
import com.mk3.chatapp.dtos.responses.UserDTO;
import com.mk3.chatapp.enums.RequiredVerificationEnum;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.TokenType;
import com.mk3.chatapp.exceptions.ConflictException;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.UserActionToken;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.*;
import com.mk3.chatapp.services.schedulers.PhoneNumberCleanupSchedulingService;
import com.mk3.chatapp.utils.IpAddressUtils;
import com.mk3.chatapp.utils.RootAdminUsers;
import com.mk3.chatapp.utils.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {
    public static final Instant DAY_DURATION = Instant.now().plus(Duration.ofDays(1));
    private static final String INVALID_PHONE_RESET_CODE_MESSAGE = "Invalid or expired reset code";
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final MailSenderService mailSenderService;
    private final UserActionTokenService tokenService;
    private final SessionManagementService sessionManagementService;

    private final ChatRoomInteractionService chatRoomInteractionService;
    private final ChatRoomService chatRoomService;

    private final UserMapper userMapper;
    private final SecurityService securityService;
    private final IpService ipService;
    private final SmsSenderService smsSenderService;
    private final PhoneNumberCleanupSchedulingService phoneNumberCleanupSchedulingService;
    private final GeolocationService geolocationService;
    private final TurnstileService turnstileService;

    @Override
    public SessionTokenDTO register(RegisterRequestDTO registerRequestDTO, HttpServletRequest httpServletRequest) {
        String ip = IpAddressUtils.getClientIpAddress(httpServletRequest);

        if (!turnstileService.verify(registerRequestDTO.captchaToken(), ip)) {
            throw new IllegalArgumentException("Invalid Captcha");
        }

        validateUser(registerRequestDTO.username(), registerRequestDTO.email(), registerRequestDTO.phoneNumber(), ip);

        var user = securityService.getCurrentUser();

        if (user == null) {
            return registerAndAuthenticateUser(registerRequestDTO, httpServletRequest);
        } else {
            return registerAndAuthenticateGuestUser(registerRequestDTO, httpServletRequest, user);
        }
    }

    private SessionTokenDTO registerAndAuthenticateGuestUser(RegisterRequestDTO registerRequestDTO,
                                                             HttpServletRequest httpServletRequest, User user) {
        if (!user.getRole().equals(Role.GUEST)) {
            throw new IllegalStateException("Only guest users can register a new account while logged in.");
        }

        var phone = (registerRequestDTO.phoneNumber() == null || registerRequestDTO.phoneNumber().isBlank()) ? null
                : smsSenderService.normalizePhoneNumber(registerRequestDTO.phoneNumber());

        String ip = IpAddressUtils.getClientIpAddress(httpServletRequest);
        String countryCode = geolocationService.determineCountryCode(ip, phone);

        user.setUsername(registerRequestDTO.username());
        user.setEmail(registerRequestDTO.email());
        user.setPassword(passwordEncoder.encode(registerRequestDTO.password()));
        boolean rootAdmin = RootAdminUsers.matches(registerRequestDTO.username(), registerRequestDTO.email());
        user.setRole(rootAdmin ? Role.SUPER_ADMIN : Role.USER);
        user.setClaimed(true);
        user.setOver18(registerRequestDTO.isOver18());
        user.setOverDigitalConsent(registerRequestDTO.isOverDigitalConsent());
        user.setAcceptsTermsAndPrivacy(registerRequestDTO.acceptsTermsAndPrivacy());
        user.setVerified(rootAdmin);
        user.setPhoneNumber(phone);
        user.setCountryCode(countryCode);

        var savedUser = userService.save(user);
        joinStaffRoomsIfRootAdmin(savedUser);

        // delete unverified numbers after a day
        if (phone != null) {
            phoneNumberCleanupSchedulingService.schedulePhoneNumberCleanup(user, DAY_DURATION);
        }

        var authenticationToken = new UsernamePasswordAuthenticationToken(
                savedUser, null, savedUser.getAuthorities());

        String sessionId = sessionManagementService.establishAndLogAuthenticatedSession(authenticationToken,
                httpServletRequest);
        return new SessionTokenDTO(sessionId);
    }

    private SessionTokenDTO registerAndAuthenticateUser(RegisterRequestDTO registerRequestDTO,
                                                        HttpServletRequest httpServletRequest) {
        var phone = (registerRequestDTO.phoneNumber() == null || registerRequestDTO.phoneNumber().isBlank()) ? null
                : smsSenderService.normalizePhoneNumber(registerRequestDTO.phoneNumber());

        String ip = IpAddressUtils.getClientIpAddress(httpServletRequest);
        String countryCode = geolocationService.determineCountryCode(ip, phone);
        boolean rootAdmin = RootAdminUsers.matches(registerRequestDTO.username(), registerRequestDTO.email());

        var user = User.builder()
                .username(registerRequestDTO.username())
                .email(registerRequestDTO.email())
                .phoneNumber(phone)
                .password(passwordEncoder.encode(registerRequestDTO.password()))
                .totalUploadUsage(0L)
                .role(rootAdmin ? Role.SUPER_ADMIN : Role.USER)
                .verified(rootAdmin)
                .claimed(true)
                .displayColor(Utils.generateRandomHexColor())
                .over18(registerRequestDTO.isOver18())
                .overDigitalConsent(registerRequestDTO.isOverDigitalConsent())
                .acceptsTermsAndPrivacy(registerRequestDTO.acceptsTermsAndPrivacy())
                .countryCode(countryCode)
                .build();

        var savedUser = userService.save(user);

        // delete unverified numbers after a day
        if (phone != null) {
            phoneNumberCleanupSchedulingService.schedulePhoneNumberCleanup(user, DAY_DURATION);
        }

        joinStaffRoomsIfRootAdmin(savedUser);

        chatRoomInteractionService.joinChatRoom(savedUser, "Home");

        var authenticationToken = new UsernamePasswordAuthenticationToken(
                savedUser, null, savedUser.getAuthorities());

        String sessionId = sessionManagementService.establishAndLogAuthenticatedSession(authenticationToken,
                httpServletRequest);
        return new SessionTokenDTO(sessionId);
    }

    @Override
    public SessionTokenDTO unclaimedRegister(UnclaimedRegisterDTO unclaimedRegisterDTO,
                                             HttpServletRequest httpServletRequest) {
        String ip = IpAddressUtils.getClientIpAddress(httpServletRequest);
        if (!turnstileService.verify(unclaimedRegisterDTO.captchaToken(), ip)) {
            throw new IllegalArgumentException("Invalid Captcha");
        }

        validateUnclaimedUser(unclaimedRegisterDTO.username());
        var user = securityService.getCurrentUser();
        if (user == null) {
            return registerAndAuthenticateAnonymousUser(unclaimedRegisterDTO, httpServletRequest);
        } else {
            return registerAndAuthenticateAnonymousGuestUser(unclaimedRegisterDTO, httpServletRequest, user);
        }
    }

    private SessionTokenDTO registerAndAuthenticateAnonymousUser(UnclaimedRegisterDTO unclaimedRegisterDTO,
                                                                 HttpServletRequest httpServletRequest) {
        String ip = IpAddressUtils.getClientIpAddress(httpServletRequest);
        String countryCode = geolocationService.determineCountryCode(ip, null);

        var user = User.builder()
                .username(unclaimedRegisterDTO.username())
                .role(Role.UNCLAIMED_USER)
                .password(UUID.randomUUID().toString())
                .totalUploadUsage(0L)
                .displayColor(Utils.generateRandomHexColor())
                .over18(unclaimedRegisterDTO.isOver18())
                .overDigitalConsent(unclaimedRegisterDTO.isOverDigitalConsent())
                .acceptsTermsAndPrivacy(unclaimedRegisterDTO.acceptsTermsAndPrivacy())
                .countryCode(countryCode)
                .build();

        var savedUser = userService.save(user);
        chatRoomInteractionService.joinChatRoom(savedUser, "Home");
        var authenticationToken = new UsernamePasswordAuthenticationToken(
                savedUser, null, savedUser.getAuthorities());
        String sessionId = sessionManagementService.establishAndLogAuthenticatedSession(authenticationToken,
                httpServletRequest);
        return new SessionTokenDTO(sessionId);
    }

    private SessionTokenDTO registerAndAuthenticateAnonymousGuestUser(UnclaimedRegisterDTO unclaimedRegisterDTO,
                                                                      HttpServletRequest httpServletRequest, User user) {
        if (!user.getRole().equals(Role.GUEST)) {
            throw new IllegalStateException("Only guest users can register a new account while logged in.");
        }

        String ip = IpAddressUtils.getClientIpAddress(httpServletRequest);
        String countryCode = geolocationService.determineCountryCode(ip, null);

        user.setUsername(unclaimedRegisterDTO.username());
        user.setRole(Role.UNCLAIMED_USER);
        user.setPassword(UUID.randomUUID().toString());
        user.setOver18(unclaimedRegisterDTO.isOver18());
        user.setOverDigitalConsent(unclaimedRegisterDTO.isOverDigitalConsent());
        user.setAcceptsTermsAndPrivacy(unclaimedRegisterDTO.acceptsTermsAndPrivacy());
        user.setVerified(false);
        user.setClaimed(false);
        user.setCountryCode(countryCode);

        var savedUser = userService.save(user);
        chatRoomInteractionService.joinChatRoom(savedUser, "Home");
        var authenticationToken = new UsernamePasswordAuthenticationToken(
                savedUser, null, savedUser.getAuthorities());
        String sessionId = sessionManagementService.establishAndLogAuthenticatedSession(authenticationToken,
                httpServletRequest);
        return new SessionTokenDTO(sessionId);
    }

    @Override
    public SessionTokenDTO login(LoginRequestDTO loginRequestDTO, HttpServletRequest request) {
        String email = loginRequestDTO.email();
        if (!hasText(email)) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        var user = userService.findOptionalByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        // Banned users may still log in: AccessRestrictionFilter limits their session to the
        // ban-appeal endpoints, /users/me and logout. Rejecting here would both lock them out
        // of the appeal flow and leak ban details before the password is verified.

        try {
            var auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getId().toString(),
                            loginRequestDTO.password()));

            String sessionId = sessionManagementService.establishAndLogAuthenticatedSession(auth, request);
            return new SessionTokenDTO(sessionId);
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException("Invalid email or password", e);
        }
    }

    @Override
    public void initiatePasswordReset(ForgotPasswordRequestDTO request) {
        boolean hasEmail = hasText(request.email());
        boolean hasPhoneNumber = hasText(request.phoneNumber());
        int providedRecoveryMethods = (hasEmail ? 1 : 0) + (hasPhoneNumber ? 1 : 0);

        if (providedRecoveryMethods != 1) {
            throw new IllegalArgumentException("Provide exactly one recovery method");
        }

        if (hasPhoneNumber) {
            initiatePhonePasswordReset(request.phoneNumber());
            return;
        }

        initiateEmailPasswordReset(request.email());
    }

    private void initiateEmailPasswordReset(String email) {
        Optional<User> userOptional = userService.findOptionalByEmail(email.trim());

        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();
        if (!canUsePasswordRecovery(user) || !hasText(user.getEmail())) {
            return;
        }

        var token = tokenService.createPasswordResetTokenForUser(user);

        try {
            mailSenderService.sendResetPasswordEmail(user, token.getToken());
        } catch (RuntimeException e) {
            log.warn("Failed to send password reset email for user id={}", user.getId(), e);
        }
    }

    private void initiatePhonePasswordReset(String phoneNumber) {
        String normalizedPhoneNumber = smsSenderService.normalizePhoneNumber(phoneNumber);
        Optional<User> userOptional = userService.findOptionalByPhoneNumber(normalizedPhoneNumber);

        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();
        if (!canUsePasswordRecovery(user) || !hasVerifiedPasswordResetPhone(user)) {
            return;
        }

        var token = tokenService.createPhonePasswordResetTokenForUser(user, normalizedPhoneNumber);
        String smsContent = "Your allchat password reset code is: " + token.getToken();
        smsSenderService.sendSMS(normalizedPhoneNumber, smsContent);
    }

    @Override
    @Transactional
    public PhonePasswordResetVerificationResponseDTO verifyPhonePasswordReset(
            PhonePasswordResetVerificationRequestDTO request) {
        String normalizedPhoneNumber = smsSenderService.normalizePhoneNumber(request.phoneNumber());
        UserActionToken userActionToken = findPhonePasswordResetToken(request.verificationCode().trim());

        if (userActionToken.getExpiryDate().isBefore(Instant.now())) {
            throw new ConflictException(INVALID_PHONE_RESET_CODE_MESSAGE);
        }

        if (!userActionToken.getType().equals(TokenType.PHONE_PASSWORD_RESET)) {
            throw new ConflictException(INVALID_PHONE_RESET_CODE_MESSAGE);
        }

        if (userActionToken.isUsed()) {
            throw new ConflictException(INVALID_PHONE_RESET_CODE_MESSAGE);
        }

        if (!normalizedPhoneNumber.equals(userActionToken.getPhoneNumber())) {
            throw new ConflictException(INVALID_PHONE_RESET_CODE_MESSAGE);
        }

        User user = userService.findById(userActionToken.getUser().getId());
        if (!canUsePasswordRecovery(user) || !hasVerifiedPasswordResetPhone(user)) {
            throw new ConflictException(INVALID_PHONE_RESET_CODE_MESSAGE);
        }

        if (!normalizedPhoneNumber.equals(user.getPhoneNumber())) {
            throw new ConflictException(INVALID_PHONE_RESET_CODE_MESSAGE);
        }

        userActionToken.setUsed(true);
        tokenService.save(userActionToken);

        UserActionToken resetToken = tokenService.createPasswordResetTokenForUser(user);
        return new PhonePasswordResetVerificationResponseDTO(resetToken.getToken());
    }

    private UserActionToken findPhonePasswordResetToken(String verificationCode) {
        try {
            return tokenService.findToken(verificationCode);
        } catch (ConflictException e) {
            throw new ConflictException(INVALID_PHONE_RESET_CODE_MESSAGE);
        }
    }

    @Override
    public UserDTO resetPassword(ResetPasswordRequestDTO request) {
        return userService.resetPassword(request);
    }

    private void validateUser(String username, String email, String phoneNumber, String ipAddress) {
        if (userService.existsByUsername(username)) {
            throw new ConflictException("Username is taken.");
        }
        if (userService.existsByEmail(email)) {
            throw new ConflictException("Email is taken.");
        }
        if (Utils.isReservedUsername(username.toLowerCase().trim())) {
            throw new ConflictException("This username is reserved and cannot be used.");
        }

        RequiredVerificationEnum requiredVerification = ipService.getRequiredVerification(ipAddress);

        if (requiredVerification.equals(RequiredVerificationEnum.PHONE)) {
            validatePhoneNumber(phoneNumber);
        }
    }

    private void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required for this registration.");
        }

        String normalized = smsSenderService.normalizePhoneNumber(phoneNumber);

        if (userService.existsByPhoneNumber(normalized)) {
            throw new ConflictException("Phone number is already used.");
        }
    }

    private boolean canUsePasswordRecovery(User user) {
        return !user.getRole().equals(Role.UNCLAIMED_USER)
                && !user.getRole().equals(Role.GUEST);
    }

    private boolean hasVerifiedPasswordResetPhone(User user) {
        return user.getPhoneNumberVerificationDate() != null;
    }

    @Override
    public ClaimAccountResponseDTO claimAccount(ClaimAccountRequestDTO claimAccountRequestDTO, Principal connectedUser,
                                                HttpServletRequest request) {
        if (userService.existsByEmail(claimAccountRequestDTO.email())) {
            throw new IllegalArgumentException("Email is already claimed");
        }

        var user = userService.getPrincipal(connectedUser);

        user.setEmail(claimAccountRequestDTO.email());
        boolean rootAdmin = RootAdminUsers.matches(user.getApplicationUsername(), claimAccountRequestDTO.email());
        user.setRole(rootAdmin ? Role.SUPER_ADMIN : Role.USER);
        user.setVerified(rootAdmin);
        user.setClaimed(true);
        user.setPassword(passwordEncoder.encode(claimAccountRequestDTO.password()));

        var savedUser = userService.save(user);
        joinStaffRoomsIfRootAdmin(savedUser);

        var auth = authenticationManager.authenticate(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        savedUser.getId().toString(),
                        claimAccountRequestDTO.password()));

        var token = sessionManagementService.establishAndLogAuthenticatedSession(auth, request);

        return new ClaimAccountResponseDTO(
                userMapper.toDto(savedUser),
                new SessionTokenDTO(token));
    }

    @Override
    public void logout(HttpServletRequest request, Principal connectedUser) {
        var user = securityService.getCurrentUser();
        String sessionId = request.getSession().getId();

        chatRoomInteractionService.disconnectUserFromChatRooms(user);

        // the session is expired in the deleteAccount method therefore the if statement
        if (user.getRole().equals(Role.UNCLAIMED_USER) || user.getRole().equals(Role.GUEST)) {
            userService.deleteAccount(user, new DeleteAccountRequest(false, null));
        } else {
            sessionManagementService.expireSessionById(sessionId);
        }
    }

    @Override
    public SessionTokenDTO registerGuest(HttpServletRequest httpServletRequest) {
        User user = userService.createGuestUser();
        chatRoomInteractionService.joinChatRoom(user, "Home");

        var authenticationToken = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());

        String sessionId = sessionManagementService.establishAndLogAuthenticatedSession(authenticationToken,
                httpServletRequest);
        return new SessionTokenDTO(sessionId);
    }

    private void validateUnclaimedUser(String username) {
        if (userService.existsByUsername(username)) {
            throw new ConflictException("Username is taken.");
        }

        if (Utils.isReservedUsername(username.toLowerCase().trim())) {
            throw new ConflictException("This username is reserved and cannot be used.");
        }

    }

    private void joinStaffRoomsIfRootAdmin(User user) {
        if (user.getRole() != Role.SUPER_ADMIN) {
            return;
        }

        var chatRooms = chatRoomService.getRoleAccessibleUserChatRooms(user.getRole());
        chatRooms.forEach(room -> chatRoomInteractionService.joinChatRoom(user, room.getName()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
