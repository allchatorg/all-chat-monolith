package com.mk3.chatapp.services;

import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.enums.RequiredVerificationEnum;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.repositories.ChatRoomRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TypingAccessService {
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final BanCacheService banCacheService;
    private final IpService ipService;
    private final MessagingAvailabilityService messagingAvailabilityService;

    // Also used after commit: a fresh read must see the new access rules, not an old managed entity.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public String authorizedUsername(Long userId, Long roomId, String ipAddress) {
        if (userId == null || roomId == null || roomId <= 0) return null;
        var user = userRepository.findById(userId).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || user.isBanned()
                || user.getRole() == Role.GUEST) return null;
        var ban = banCacheService.getBanByUserId(userId);
        if (ban != null && ban.isActive()) return null;
        var verification = user.getIdVerificationStatus();
        if (verification == IdVerificationStatus.REQUIRED || verification == IdVerificationStatus.PENDING
                || verification == IdVerificationStatus.REJECTED) return null;
        var required = ipAddress == null ? RequiredVerificationEnum.NONE : ipService.getRequiredVerification(ipAddress);
        if (required != RequiredVerificationEnum.NONE && !user.isClaimed()) return null;
        if (required == RequiredVerificationEnum.EMAIL && !user.isVerified()) return null;
        if (required == RequiredVerificationEnum.PHONE
                && (!user.isVerified() || user.getPhoneNumberVerificationDate() == null)) return null;

        var room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null || Boolean.TRUE.equals(room.getDeleted()) || room.isArchived()
                || room.getRequiredAccessLevel().getLevel() > user.getRole().getLevel()) return null;
        var membership = userChatRoomRepository.findUserChatRoomByUserAndChatRoom(user, room).orElse(null);
        if (membership == null || Boolean.TRUE.equals(membership.getDeleted())) return null;

        if (room.getType() == ChatRoomType.PRIVATE) {
            if (!user.isClaimed() || !user.getRole().isStaffMember()) return null;
            var counterpart = userChatRoomRepository.findByChatRoom(room).stream()
                    .map(member -> member.getUser())
                    .filter(member -> !Objects.equals(member.getId(), userId)).findFirst().orElse(null);
            if (counterpart == null || Boolean.TRUE.equals(counterpart.getDeleted())
                    || user.getBlockedUsers().stream().anyMatch(blocked -> blocked.getId().equals(counterpart.getId()))
                    || counterpart.getBlockedUsers().stream().anyMatch(blocked -> blocked.getId().equals(userId))) return null;
        } else if (messagingAvailabilityService.getCurrentAvailability().messagingBlocked()) {
            return null;
        }
        return user.getApplicationUsername();
    }
}
