package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.dtos.WarnUserRequestDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.*;
import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.mappers.AuditLogCustomMapper;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.AuditLog;
import com.mk3.chatapp.models.UsernameHistory;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UsernameHistoryRepository;
import com.mk3.chatapp.services.*;
import com.mk3.chatapp.specifications.AuditLogSpecification;
import com.mk3.chatapp.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFacadeServiceImpl implements AdminFacadeService {
    private final BanService banService;
    private final AdsModerationPort adsModerationPort;
    private final MessagePromotionPort messagePromotionPort;
    private final UserService userService;
    private final SecurityService securityService;
    private final MessagesService messagesService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final RoleManagementService roleManagementService;
    private final SessionManagementService sessionManagementService;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final AuditLogService auditLogService;
    private final ChatRoomService chatRoomService;
    private final RoomActivityService roomActivityService;
    private final IdVerificationService idVerificationService;
    private final NotificationService notificationService;

    private final UserMapper userMapper;
    private final AuditLogCustomMapper auditLogCustomMapper;

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.mk3.chatapp.enums.Permission).BAN_USERS.getPermission())")
    public AuditLog banUser(BanRequestDTO banRequestDTO) {
        AuditLog auditLog = banService.banUser(banRequestDTO, securityService.getCurrentUser());

        // The ban transaction is committed inside banService.banUser, so the
        // refunds run after it — a payment-provider failure can never fail or
        // roll back the ban.
        if (banRequestDTO.banType() == BanType.PERMANENT) {
            try {
                var result = adsModerationPort.refundPendingAdPurchases(banRequestDTO.userId());
                if (result.attempted() > 0) {
                    log.info("Permanent ban of user {}: refunded {}/{} pending ad purchase(s), total {} {}",
                            banRequestDTO.userId(), result.refunded(), result.attempted(),
                            result.totalRefunded(), result.currency());
                }
            } catch (Exception e) {
                log.error("Failed to refund pending ad purchases for banned user {}",
                        banRequestDTO.userId(), e);
            }

            // Own try/catch — the ban must never fail on a payment-provider error.
            try {
                var promotionResult = messagePromotionPort.cancelPromotionsForBannedUser(banRequestDTO.userId());
                if (promotionResult.attempted() > 0) {
                    log.info("Permanent ban of user {}: released {}/{} pending promotion hold(s), total {} {}",
                            banRequestDTO.userId(), promotionResult.released(), promotionResult.attempted(),
                            promotionResult.totalReturned(), promotionResult.currency());
                }
            } catch (Exception e) {
                log.error("Failed to cancel promoted messages for banned user {}",
                        banRequestDTO.userId(), e);
            }
        }
        return auditLog;
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.mk3.chatapp.enums.Permission).BAN_USERS.getPermission())")
    public void revokeBan(Long targetUserId) {
        User targetUser = userService.findById(targetUserId);
        banService.revokeBan(targetUser);
    }

    @Override
    public UserDTO getUserAdminDetails(Long userId) {
        var user = userService.findById(userId);
        return userMapper.toDto(user);
    }

    @Override
    public Page<MessageResponseDTO> getUserMessages(MessageSearchRequestDTO request) {
        var requestSender = securityService.getCurrentUser();
        var targetUser = userService.findByUsername(request.senderUsername());

        if (!requestSender.getId().equals(targetUser.getId())
                && !requestSender.getRole().canActOn(targetUser.getRole())) {
            throw new AccessDeniedException("You cannot act on this user");
        }

        return messagesService.searchMessages(request.chatRoomId(), request, requestSender.getRole());
    }

    @Override
    public AuditLog warnUser(WarnUserRequestDTO warnRequestDTO) {
        User targetUser = userService.findById(warnRequestDTO.userId());
        notificationService.createAndSend(targetUser, NotificationType.WARNING,
                "You received a warning", warnRequestDTO.description(), null, null, null);
        return auditLogService.logWarning("WARN_USER", warnRequestDTO.description(), targetUser.getId());
    }

    @Override
    public Page<BanResponseDTO> findActiveBans(String userNameOrId, int page, int pageSize) {
        Long userId = null;
        String username = null;
        try {
            userId = Long.parseLong(userNameOrId);
        } catch (NumberFormatException e) {
            username = userNameOrId;
        }

        return banService.findActiveBans(username, userId, page, pageSize);
    }

    @Override
    public void updateUserRole(RoleUpdateRequest request) {
        var user = userService.findById(request.userId());
        Role role = request.role();
        roleManagementService.updateUserRole(user, role);
    }

    @Override
    public Page<UserDTO> searchUsers(UserSearchRequestDTO request) {
        Page<User> usersPage = userService.searchUsers(request);
        return usersPage.map(userMapper::toDto);
    }

    @Override
    public UserAdminViewDTO getUserAdminViewDetails(Long userId) {

        User user = userService.findById(userId);
        var lastSession = sessionManagementService.getLastSessionInfo(userId);

        if (lastSession.isEmpty()) {
            throw new IllegalStateException("User has no sessions");
        }

        List<String> usernameHistory = usernameHistoryRepository.findAllByUser(user).stream()
                .map(UsernameHistory::getUsername).toList();

        return new UserAdminViewDTO(user.getId(), user.getApplicationUsername(), user.getEmail(), user.isOver18(),
                user.isClaimed(), user.isVerified(), user.isBanned(), user.getIdVerificationStatus(), user.getRole(),
                user.getTotalUploadUsage(), usernameHistory, user.getCreatedAt(), lastSession.get().getCreatedAt(),
                user.getCountryCode());
    }

    @Override
    public Page<AuditLogDTO> searchAuditLogs(AuditLogSearchRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        if (request.createdByType() == com.mk3.chatapp.enums.AuditLogActorType.SYSTEM
                && request.createdByUserId() != null) {
            throw new IllegalArgumentException("System-created audit logs cannot be filtered by user id");
        }

        if (request.page() < 0 || request.size() <= 0 || request.size() > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        List<Sort.Order> sortOrders = Utils.jsonStringToSortOrder(request.sort());
        sortOrders.add(new Sort.Order(Sort.Direction.DESC, "createdAt"));

        var pageRequest = PageRequest.of(request.page(), request.size(), Sort.by(sortOrders));

        var specification = AuditLogSpecification.getCompleteSpecification(request);

        Page<AuditLog> auditLogs = auditLogService.findAll(specification, pageRequest);
        return auditLogs.map(auditLogCustomMapper::toAuditLogDTO);
    }

    @Override
    @PreAuthorize("@security.isAdmin()")
    public void archiveChatRoom(Long roomId) {
        var chatRoom = chatRoomService.findById(roomId);
        chatRoomService.archiveChatRoom(roomId);
        roomActivityService.markRoomAsArchived(roomId.toString());
        webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(),
                new WebSocketMessage(WebSocketMessageType.CHATROOM_ARCHIVED, chatRoom.getName(),
                        buildChatRoomStatusPayload(chatRoom.getId(), chatRoom.getName(), true)));
        auditLogService.logArchiveChatRoom("ARCHIVE_CHATROOM", "Archived chat room: " + chatRoom.getName(), roomId, chatRoom.getName());
    }

    @Override
    @PreAuthorize("@security.isAdmin()")
    public void unarchiveChatRoom(Long roomId) {
        var chatRoom = chatRoomService.findById(roomId);
        chatRoomService.unarchiveChatRoom(roomId);
        roomActivityService.markRoomAsUnarchived(roomId.toString());
        webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(),
                new WebSocketMessage(WebSocketMessageType.CHATROOM_UNARCHIVED, chatRoom.getName(),
                        buildChatRoomStatusPayload(chatRoom.getId(), chatRoom.getName(), false)));
        auditLogService.logUnarchiveChatRoom("UNARCHIVE_CHATROOM", "Unarchived chat room: " + chatRoom.getName(), roomId, chatRoom.getName());
    }

    private ChatRoomDTO buildChatRoomStatusPayload(Long chatRoomId, String chatRoomName, boolean archived) {
        return new ChatRoomDTO(chatRoomId, chatRoomName, List.of(), archived);
    }

    @Override
    public void requireIdVerification(Long userId, Long reportCaseId) {
        idVerificationService.requireIdVerification(userId, reportCaseId);
    }

    @Override
    public void clearIdVerificationRequirement(Long userId) {
        idVerificationService.clearIdVerificationRequirement(userId);
    }
}
