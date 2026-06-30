package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.dtos.BanUserNotificationDTO;
import com.mk3.chatapp.dtos.requests.BanRequestDTO;
import com.mk3.chatapp.dtos.responses.BanUserMessageDTO;
import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.mappers.BanMapper;
import com.mk3.chatapp.models.*;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.BanRepository;
import com.mk3.chatapp.services.*;
import com.mk3.chatapp.services.schedulers.BanSchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class BanServiceImpl implements BanService {

    private final BanRepository banRepository;

    private final BanCacheService banCacheService;
    private final SecurityService securityService;
    private final BanSchedulingService banSchedulingService;

    private final SessionManagementService sessionManagementService;
    private final UserService userService;
    private final MessagesService messagesService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final BanMapper banMapper;
    private final com.mk3.chatapp.services.AuditLogService auditLogService;
    private final IpService ipService;

    @Transactional
    @Override
    public AuditLog banUser(BanRequestDTO banRequestDTO, User currentUser) {
        var targetUser = userService.findById(banRequestDTO.userId());

        validateBanRequest(banRequestDTO.banType(), banRequestDTO.duration(), currentUser, targetUser);
        String description = normalizeDescription(banRequestDTO.description());

        LastSessionInfo lastSessionInfo = sessionManagementService.getLastSessionInfo(targetUser.getId()).orElse(null);

        var ban = createAndSaveBan(banRequestDTO, targetUser, lastSessionInfo, description);

        if (ban.getType() == BanType.TEMPORARY) {
            banSchedulingService.scheduleUnban(ban);
        }

        // Audit log for ban action
        var log = auditLogService.logBan(
                "BAN_USER",
                description,
                targetUser.getId(),
                banRequestDTO.banType(),
                banRequestDTO.reportType(),
                banRequestDTO.duration() != null ? banRequestDTO.duration().getSeconds() : null,
                banRequestDTO.deleteMessages(),
                banRequestDTO.deleteMessagesDuration() != null ? banRequestDTO.deleteMessagesDuration().getSeconds()
                        : null);

        notifyUserViaWebSocket(banRequestDTO, targetUser, ban, description);
        handleMessageDeletion(banRequestDTO, targetUser);

        broadcastBanToUserChatRooms(targetUser.getUserChatRooms(), ban, banRequestDTO.deleteMessages(),
                getDeleteMessagesAfter(banRequestDTO));
        sessionManagementService.expireUserSessions(targetUser.getId());
        banCacheService.addBanEntry(ban);

        ipService.flagBannedIp(targetUser.getRole(), lastSessionInfo.getIpAddress());

        targetUser.setBanned(true);
        userService.save(targetUser);
        return log;
    }

    @Override
    @Transactional
    public void systemRevokeBan(User targetUser) {

        Ban ban = banRepository.findByUserAndActiveIs(targetUser, true).orElseThrow(
                () -> new IllegalArgumentException("No active ban found for user ID: " + targetUser.getId()));

        if (ban.getReportType().isCsamRelated()) {
            throw new IllegalArgumentException("Cannot revoke a CSAM ban.");
        }

        banSchedulingService.cancelUnbanJob(targetUser);

        ban.setActive(false);
        banRepository.save(ban);

        banCacheService.removeBanEntry(ban);

        targetUser.setBanned(false);
        userService.save(targetUser);
    }

    @Override
    @Transactional
    public void revokeBan(User targetUser) {
        systemRevokeBan(targetUser);

        com.mk3.chatapp.models.identity.User currentUser = null;
        try {
            currentUser = securityService.getCurrentUser();
        } catch (org.springframework.security.authentication.AuthenticationCredentialsNotFoundException ignored) {
            // Unauthenticated contexts (like background scheduled jobs) will throw this.
        }

        if (currentUser != null) {
            auditLogService.logRevokeBan(
                    "REVOKE_BAN",
                    "Revoked ban for userId " + targetUser.getId(),
                    targetUser.getId());
        }
    }

    @Override
    public Page<BanResponseDTO> findActiveBans(String username, Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);

        Page<Ban> bansPage = banRepository.findActiveBans(username, userId, pageable);

        return bansPage.map(banMapper::toDto);
    }

    @Override
    public Optional<Ban> findUserActiveBan(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        return banRepository.findByUserAndActiveIs(user, true);
    }

    @Override
    @Transactional
    public void reScheduleBans() {

        List<Ban> activeTempBans = banRepository
                .findByActiveIsTrueAndExpiresAtIsNotNullAndExpiresAtAfter(Instant.now());
        List<Ban> expiredBans = banRepository.findByActiveIsTrueAndExpiresAtIsNotNullAndExpiresAtBefore(Instant.now());

        for (Ban ban : activeTempBans) {
            banSchedulingService.scheduleUnban(ban);
        }

        for (Ban ban : expiredBans) {
            revokeBan(ban.getUser());
        }
    }

    private void validateBanRequest(BanType banType, Duration banDuration, User currentUser, User targetUser) {
        if (banType == BanType.TEMPORARY && banDuration == null) {
            throw new IllegalArgumentException("Temporary bans must have a duration specified.");
        }

        if (banType == BanType.PERMANENT && banDuration != null) {
            throw new IllegalArgumentException("Permanent bans should not have a duration specified.");
        }

        if (!currentUser.getRole().canActOn(targetUser.getRole())) {
            throw new IllegalArgumentException("You do not have permission to ban this user.");
        }

        if (currentUser.getId().equals(targetUser.getId())) {
            throw new IllegalArgumentException("You cannot ban yourself.");
        }
    }

    private void handleMessageDeletion(BanRequestDTO dto, User user) {
        if (!dto.deleteMessages())
            return;

        Instant cutoff;
        if (dto.deleteMessagesDuration() == null) {
            cutoff = Instant.EPOCH;
        } else {
            cutoff = Instant.now().minus(dto.deleteMessagesDuration());
        }

        messagesService.deleteUserMessagesAfter(user, cutoff);
    }

    private void notifyUserViaWebSocket(BanRequestDTO dto, User user, Ban ban, String description) {
        ReportType userFacingReportType = dto.reportType().toUserFacingReportType();
        String userFacingDescription = dto.reportType().toUserFacingDescription(description);

        BanUserMessageDTO message = new BanUserMessageDTO(userFacingReportType, dto.banType(),
                dto.banType() == BanType.PERMANENT, ban.getExpiresAt(), userFacingDescription);

        webSocketBroadcastService.broadcastToUser(user.getId(),
                new WebSocketMessage(WebSocketMessageType.BAN_USER, null, message));
    }

    private Ban createAndSaveBan(BanRequestDTO dto, User user, LastSessionInfo session, String description) {
        Instant expiresAt = dto.banType() == BanType.TEMPORARY ? Instant.now().plus(dto.duration()) : null;

        Ban ban = Ban.builder()
                .user(user)
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .description(description).type(dto.banType())
                .reportType(dto.reportType())
                .expiresAt(expiresAt)
                .active(true)
                .build();

        return banRepository.save(ban);
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        String trimmedDescription = description.trim();
        return trimmedDescription.isEmpty() ? null : trimmedDescription;
    }

    private Instant getDeleteMessagesAfter(BanRequestDTO dto) {
        Instant startOfTime = Instant.EPOCH;

        if (dto.deleteMessages() && dto.deleteMessagesDuration() != null) {
            return Instant.now().minus(dto.deleteMessagesDuration());
        } else {
            return startOfTime;
        }
    }

    private void broadcastBanToUserChatRooms(List<UserChatRoom> userChatRooms, Ban ban, boolean deleteMessages,
                                             Instant deleteMessagesAfter) {
        Long userId = ban.getUser().getId();
        userChatRooms.forEach(userChatRoom -> {
            String roomName = userChatRoom.getChatRoom().getName();
            var banNotifier = new BanUserNotificationDTO(userId, roomName, ban.getType(), deleteMessages,
                    deleteMessagesAfter != null ? deleteMessagesAfter.toString() : null);
            var socketMessage = new WebSocketMessage(WebSocketMessageType.BAN_USER_CHAT_NOTIFICATION, roomName,
                    banNotifier);
            webSocketBroadcastService.broadcastToChatRoom(roomName, socketMessage);
        });
    }
}
