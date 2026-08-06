package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.ResetPasswordRequestDTO;
import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.UserDTO;
import com.mk3.chatapp.dtos.responses.UserMinimalDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.TokenType;
import com.mk3.chatapp.exceptions.ConflictException;
import com.mk3.chatapp.mappers.TagMapper;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Tag;
import com.mk3.chatapp.models.UserActionToken;
import com.mk3.chatapp.models.UsernameHistory;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.BanRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.repositories.UsernameHistoryRepository;
import com.mk3.chatapp.services.*;
import com.mk3.chatapp.services.schedulers.PhoneNumberCleanupSchedulingService;
import com.mk3.chatapp.specifications.UserSpecification;
import com.mk3.chatapp.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    public static final Instant STALE_ACCOUNT_DELETION_CUTOFF_TIME = Instant.now().minusSeconds(7 * 24 * 60 * 60);
    private final UserRepository userRepository;
    private final BanRepository banRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserActionTokenService tokenService;
    private final MailSenderServiceImpl mailSenderService;
    private final TagServiceImpl tagService;
    private final AttachmentService attachmentService;
    private final MessagesService messagesService;
    private final SecurityService securityService;
    private final TagMapper tagMapper;
    private final SessionManagementService sessionManagementService;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final SmsSenderService smsSenderService;
    private final PhoneNumberCleanupSchedulingService phoneNumberCleanupSchedulingService;
    private final GeolocationService geolocationService;

    @Override
    public void changePassword(ChangePasswordRequestDTO request, Principal connectedUser) {
        var user = getPrincipal(connectedUser);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalStateException("Wrong password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserMinimalDTO> searchClaimedUsersForChat(String query, Principal connectedUser, int page, int size) {
        var requester = getPrincipal(connectedUser);
        if (!requester.isClaimed()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only claimed users can search for users to chat with");
        }
        if (query == null || query.isBlank()) {
            return Page.empty();
        }
        if (page < 0 || size <= 0 || size > 50) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        String like = query.trim().toLowerCase() + "%";
        Long requesterId = requester.getId();
        List<Long> blockedByRequester = requester.getBlockedUsers().stream().map(User::getId).toList();

        Specification<User> spec = (root, q, cb) -> {
            var conjuncts = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            conjuncts.add(cb.like(cb.lower(root.get("username")), like));
            conjuncts.add(cb.isTrue(root.get("claimed")));
            conjuncts.add(cb.isFalse(root.get("banned")));
            // Private messaging is staff-only, so only surface staff as DM targets.
            conjuncts.add(root.get("role").in(Role.MODERATOR, Role.ADMIN, Role.SUPER_ADMIN));
            conjuncts.add(cb.notEqual(root.get("id"), requesterId));
            conjuncts.add(cb.isFalse(root.get("deleted")));
            if (!blockedByRequester.isEmpty()) {
                conjuncts.add(cb.not(root.get("id").in(blockedByRequester)));
            }
            var blockedSub = q.subquery(Long.class);
            var blocker = blockedSub.from(User.class);
            var blockedJoin = blocker.join("blockedUsers");
            blockedSub.select(blocker.get("id"))
                    .where(cb.and(
                            cb.equal(blocker.get("id"), root.get("id")),
                            cb.equal(blockedJoin.get("id"), requesterId)));
            conjuncts.add(cb.not(cb.exists(blockedSub)));
            return cb.and(conjuncts.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "username"));
        return userRepository.findAll(spec, pageable).map(userMapper::toMinimalDto);
    }

    @Override
    public Page<User> searchUsers(UserSearchRequestDTO userSearchRequestDTO) {
        if (userSearchRequestDTO == null) {
            throw new IllegalArgumentException("User search request cannot be null");
        }

        if (userSearchRequestDTO.page() < 0 || userSearchRequestDTO.size() <= 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        List<SortDto> sortDtos = Utils.jsonStringToSortDto(userSearchRequestDTO.sort());

        List<Sort.Order> sortOrders = sortDtos
                .stream()
                .map(sortDto -> new Sort.Order(
                        Sort.Direction.fromString(sortDto.direction()),
                        sortDto.field()))
                .toList();

        PageRequest pageRequest = PageRequest.of(
                userSearchRequestDTO.page(),
                userSearchRequestDTO.size(),
                Sort.by(sortOrders));

        Specification<User> specification = UserSpecification.getSpecification(
                userSearchRequestDTO);

        return userRepository.findAll(specification, pageRequest);
    }

    @Override
    public User createGuestUser() {
        String guestColor = Utils.generateRandomHexColor();
        String guestUsername = MessageFormat.format("guest_{0}",
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        User guestUser = User.builder()
                .username(guestUsername)
                .password(null)
                .email(null)
                .over18(false)
                .verified(false)
                .claimed(false)
                .banned(false)
                .totalUploadUsage(0L)
                .role(Role.GUEST)
                .displayColor(guestColor)
                .build();

        return userRepository.save(guestUser);
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    @Override
    public Optional<User> findOptionalByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Override
    public Optional<User> findOptionalByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        return userRepository.save(user);
    }

    @Override
    public UserDTO resetPassword(ResetPasswordRequestDTO request) {
        var userActionToken = tokenService.findToken(request.token());

        if (userActionToken == null) {
            throw new IllegalArgumentException("Token not found");
        }
        if (userActionToken.isUsed()) {
            throw new IllegalArgumentException("Token has already been used");
        }
        if (userActionToken.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token has expired");
        }
        if (!userActionToken.getType().equals(TokenType.PASSWORD_RESET)) {
            throw new IllegalArgumentException("Token is not a password reset token");
        }

        var user = userActionToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));

        userActionToken.setUsed(true);
        tokenService.save(userActionToken);
        return userMapper.toDto(save(user));
    }

    @Override
    public void sendEmailVerification(Principal connectedUser) {
        long userId = Long.parseLong(connectedUser.getName());

        User user = findById(userId);
        var token = tokenService.createEmailVerificationTokenForUser(user);

        mailSenderService.sendVerificationEmail(user, token.getToken());
    }

    @Override
    public UserDTO verifyEmail(String token) {
        var userActionToken = tokenService.findToken(token);
        if (userActionToken.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token has expired");
        }

        if (!userActionToken.getType().equals(TokenType.EMAIL_VERIFICATION)) {
            throw new IllegalArgumentException("Token is not a email verification token");
        }

        if (userActionToken.isUsed()) {
            throw new IllegalArgumentException("Token has already been used");
        }

        var user = userActionToken.getUser();
        user = findById(user.getId());

        var connectedUser = securityService.getCurrentUser();
        if (connectedUser == null || !connectedUser.getId().equals(user.getId())) {
            throw new ConflictException("User does not match the token owner");
        }

        user.setVerified(true);
        userActionToken.setUsed(true);
        tokenService.save(userActionToken);
        return userMapper.toDto(save(user));
    }

    @Override
    public void sendPhoneVerification(AddPhoneNumberRequest phoneNumberRequest) {
        String phoneNumber = smsSenderService.normalizePhoneNumber(phoneNumberRequest.phoneNumber());
        var currentUser = securityService.getCurrentUser();

        if (existsByPhoneNumber(phoneNumber)) {
            throw new ConflictException("Phone number is already used.");
        }

        UserActionToken token = tokenService.createPhoneVerificationToken(currentUser, phoneNumber);
        String smsContent = "Your allchat verification code is: " + token.getToken();
        smsSenderService.sendSMS(phoneNumber, smsContent);
    }

    @Override
    public void removeUserPhoneNumber(User user) {
        user.setPhoneNumber(null);
        user.setPhoneNumberVerificationDate(null);

        save(user);
    }

    @Override
    public UserDTO verifyPhone(String token) {
        var userActionToken = tokenService.findToken(token);
        if (userActionToken.getExpiryDate().isBefore(Instant.now())) {
            throw new ConflictException("Token has expired");
        }

        if (!userActionToken.getType().equals(TokenType.PHONE_VERIFICATION)) {
            throw new ConflictException("Token is not a phone verification token");
        }

        if (userActionToken.isUsed()) {
            throw new ConflictException("Token has already been used");
        }

        var user = userActionToken.getUser();
        user = findById(user.getId());

        var connectedUser = securityService.getCurrentUser();

        if (!connectedUser.getId().equals(user.getId())) {
            throw new ConflictException("User does not match the token owner");
        }

        user.setPhoneNumber(userActionToken.getPhoneNumber());
        user.setCountryCode(geolocationService.determineCountryCode(null, userActionToken.getPhoneNumber()));
        user.setPhoneNumberVerificationDate(Instant.now());
        userActionToken.setUsed(true);
        tokenService.save(userActionToken);
        return userMapper.toDto(save(user));
    }

    @Override
    public User getPrincipal(Principal connectedUser) {

        if (connectedUser == null) {
            throw new IllegalArgumentException("Connected user is null");
        }

        String username = connectedUser.getName();
        var user = findById(Long.parseLong(username));

        return user;
    }

    @Override
    public UserDTO changeUsername(String username, Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        if (user.getApplicationUsername().equals(username)) {
            throw new IllegalArgumentException("Username is the same as the current one");
        }
        if (existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (Utils.isReservedUsername(username.toLowerCase().trim())) {
            throw new IllegalArgumentException("This username is reserved and cannot be used.");
        }

        UsernameHistory history = UsernameHistory.builder()
                .user(user)
                .username(user.getApplicationUsername())
                .build();

        usernameHistoryRepository.save(history);

        user.setUsername(username);
        return userMapper.toDto(save(user));
    }

    @Override
    public void requestEmailUpdate(RequestEmailUpdateDTO request, Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        if (user.getPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalStateException("Wrong password");
        }

        String newEmail = request.newEmail().trim();
        if (user.getEmail() != null && user.getEmail().equalsIgnoreCase(newEmail)) {
            throw new IllegalArgumentException("New email is the same as the current one");
        }

        if (existsByEmail(newEmail)) {
            throw new IllegalArgumentException("Email is already in use");
        }

        UserActionToken token = tokenService.createEmailUpdateTokenForUser(user, newEmail);
        mailSenderService.sendEmailUpdateVerification(user, newEmail, token.getToken());
    }

    @Override
    @Transactional
    public UserDTO verifyEmailUpdate(VerifyEmailUpdateDTO request, Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        UserActionToken token = tokenService.findToken(request.verificationCode());

        if (!token.getType().equals(TokenType.EMAIL_UPDATE)) {
            throw new IllegalArgumentException("Token is not an email update token");
        }

        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token has expired");
        }

        if (token.isUsed()) {
            throw new IllegalArgumentException("Token has already been used");
        }

        if (!token.getUser().getId().equals(user.getId())) {
            throw new ConflictException("Token does not belong to the authenticated user");
        }

        if (existsByEmail(token.getEmail()) && (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(token.getEmail()))) {
            throw new IllegalArgumentException("Email is already in use");
        }

        user.setEmail(token.getEmail());
        user.setVerified(true);
        token.setUsed(true);
        tokenService.save(token);
        return userMapper.toDto(save(user));
    }

    @Override
    public void updateMarketingPreferences(UpdateMarketingPreferencesDTO request, Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        user.setSubscribedToMarketingEmails(request.subscribedToMarketingEmails());
        save(user);
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @Override
    public void updateAge(boolean isOver18, Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        user.setOver18(isOver18);
        save(user);
    }

    @Override
    @Transactional
    public UserDTO getUserInfo(Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        user.setLastSeen(Instant.now());
        return userMapper.toDto(save(user));
    }

    @Override
    public List<TagDTO> updateBlurredContent(List<Long> tagIds, Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        List<Tag> blurredTags = tagService.findByIds(tagIds);
        user.setBlurredContentTags(blurredTags);
        save(user);
        return blurredTags.stream().map(
                tagMapper::toDto).toList();
    }

    @Transactional
    @Override
    public void deleteOwnAccount(User user, DeleteAccountRequest deleteAccountRequest) {
        if (user.getRole().isStaffMember()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Staff members cannot delete their account. Step down from your role first.");
        }
        // Guest/unclaimed accounts have no password, so only claimed accounts are challenged
        if (user.getPassword() != null
                && (deleteAccountRequest.password() == null
                || !passwordEncoder.matches(deleteAccountRequest.password(), user.getPassword()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong password");
        }
        deleteAccount(user, deleteAccountRequest);
    }

    @Transactional
    @Override
    public void deleteAccount(User user, DeleteAccountRequest deleteAccountRequest) {
        user.setDeleted(true);
        user.setEmail(null);
        user.getUserChatRooms().clear();
        user.getBlurredContentTags().clear();
        user.setOver18(false);
        user.setClaimed(false);
        user.setVerified(false);
        user.setSubscribedToMarketingEmails(false);
        user.setPassword(null);
        setDeletedUsername(user);

        if (deleteAccountRequest.removeMessages()) {
            messagesService.deleteAllUserMessages(user);
        }

        userRepository.save(user);

        sessionManagementService.expireUserSessions(user.getId());
    }

    @Override
    public Long incrementTotalUploadedFilesSize(Long size, User user) {
        if (size == null || size < 0) {
            throw new IllegalArgumentException("Size must be a non-negative value");
        }

        user.setTotalUploadUsage(user.getTotalUploadUsage() + size);
        userRepository.save(user);
        return user.getTotalUploadUsage();
    }

    @Override
    public String updateUserDisplayColor(String color) {
        if (!Utils.isValidHexColor(color)) {
            throw new IllegalArgumentException("Invalid color format");
        }

        User user = securityService.getCurrentUser();
        user.setDisplayColor(color.toUpperCase());
        userRepository.save(user);

        return color.toUpperCase();
    }

    @Override
    @Transactional
    public void deleteStaleAccounts() {
        var users = userRepository.findStaleGuestAndUnclaimedUsers(STALE_ACCOUNT_DELETION_CUTOFF_TIME);
        for (User user : users) {
            deleteAccount(user, new DeleteAccountRequest(false, null));
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        long userId = -1L;
        try {
            userId = Long.parseLong(username);
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("Invalid user ID format");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                user.getPassword(),
                user.getAuthorities());
    }

    private void setDeletedUsername(User user) {
        UsernameHistory history = new UsernameHistory();
        history.setUsername(user.getApplicationUsername());
        history.setUser(user);

        String deletedUsername = MessageFormat.format("deleted_{0}",
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        user.getUsernameHistory().add(history);
        user.setUsername(deletedUsername);
    }

    @Override
    @Transactional
    public void blockUser(Long userId, Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        if (user.getId().equals(userId)) {
            throw new IllegalArgumentException("You cannot block yourself");
        }
        var userToBlock = findById(userId);

        if (userToBlock.getRole().isStaffMember()) {
            throw new IllegalArgumentException("You cannot block a staff member");
        }

        if (!user.getBlockedUsers().contains(userToBlock)) {
            user.getBlockedUsers().add(userToBlock);
            save(user);
        }
    }

    @Override
    @Transactional
    public void unblockUser(Long userId, Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        var userToUnblock = findById(userId);
        if (user.getBlockedUsers().contains(userToUnblock)) {
            user.getBlockedUsers().remove(userToUnblock);
            save(user);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserMinimalDTO> getBlockedUsers(Principal connectedUser) {
        var user = getPrincipal(connectedUser);
        return user.getBlockedUsers().stream()
                .map(userMapper::toMinimalDto)
                .toList();
    }

    @Override
    public List<User> findStaffMembers() {
        return userRepository.findByRoleIn(List.of(Role.MODERATOR, Role.ADMIN, Role.SUPER_ADMIN));
    }

    @Override
    @Transactional
    public void quarantineUser(User user) {
        user.setQuarantined(true);
        for (Message message : user.getMessages()) {
            message.setQuarantined(true);
        }
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void submitModeratorApplication(Principal connectedUser,
                                           ModeratorApplicationRequest request) {
        User user = getPrincipal(connectedUser);

        if (user.getRole() != Role.USER) {
            throw new org.springframework.security.access.AccessDeniedException("Only users can apply for moderator");
        }

        if (!user.isVerified()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User must be verified to apply for moderator");
        }

        mailSenderService.sendModeratorApplicationEmail(user, request);

        user.setAppliedForModerator(true);
        save(user);
    }
}
