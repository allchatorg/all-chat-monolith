package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.AppearanceDTO;
import com.mk3.chatapp.dtos.responses.VipBadgeDTO;
import com.mk3.chatapp.events.VipBadgeChangedEvent;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
@RequiredArgsConstructor
public class VipBadgeService {
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private final VipFontService fonts;

    /** Check current paid or staff access in the database, independent of cached roles and badge preferences. */
    @Transactional(readOnly = true)
    public boolean hasActiveEntitlement(Long userId) {
        return userId != null && (userRepository.findEligibleVipPaidThrough(userId)
                .filter(paidThrough -> paidThrough.isAfter(Instant.now())).isPresent()
                || userRepository.hasStaffVipAccess(userId));
    }

    /** Caller holds the user row lock, shared with all subscription mutations. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateEntitlement(User user, Instant paidThrough) {
        // A late renewal must not revive choices whose local entitlement expired.
        fonts.resetExpiredPreferences(user);
        boolean previouslyVisible = user.isVipBadgeVisible();
        user.setVipPaidThrough(paidThrough);
        persistVisibility(user, previouslyVisible != user.isVipBadgeVisible());
    }

    /** Also called by the expiry sweep: the last published flag survives passage of time. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshVisibility(User user) {
        persistVisibility(user, false);
    }

    /** Publish entitlement changes even when the user has hidden their public badge. Caller holds the row lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshRoleEntitlement(User user) {
        persistVisibility(user, true);
    }

    private void persistVisibility(User user, boolean forceRevision) {
        fonts.resetExpiredPreferences(user);
        boolean visible = user.isVipBadgeVisible();
        if (forceRevision || visible != user.isVipBadgeLastPublishedVisible()) {
            user.setVipBadgeLastPublishedVisible(visible);
            user.setVipBadgeRevision(user.getVipBadgeRevision() + 1);
            events.publishEvent(new VipBadgeChangedEvent(toDto(user)));
        }
        // Ordinary profile/last-seen saves must never overwrite these fields from
        // an older entity snapshot. Their ORM columns are deliberately read-only.
        userRepository.updateVipState(user.getId(), user.getVipPaidThrough(), user.isShowVipBadge(),
                user.getVipBadgeRevision(), user.isVipBadgeLastPublishedVisible());
    }

    @Transactional
    public void expireBadge(Long userId) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) return;
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        refreshVisibility(user);
    }

    @Transactional
    public AppearanceDTO updatePreference(Long userId, boolean showVipBadge) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        user.setShowVipBadge(showVipBadge);
        refreshVisibility(user);
        return new AppearanceDTO(user.isShowVipBadge(), user.isVipActive(), user.isVipBadgeVisible(),
                user.getVipBadgeRevision());
    }

    @Transactional(readOnly = true)
    public List<VipBadgeDTO> lookup(List<Long> userIds) {
        if (userIds == null || userIds.size() > 100 || userIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at most 100 positive user IDs");
        }
        if (userIds.isEmpty()) return List.of();
        return userRepository.findByIdIn(userIds.stream().distinct().toList()).stream()
                .map(VipBadgeService::toDto).toList();
    }

    private static VipBadgeDTO toDto(User user) {
        return new VipBadgeDTO(user.getId(), user.isVipBadgeVisible(), user.getVipBadgeRevision(),
                user.getEffectiveUsernameFont(), user.getEffectiveMessageFont(), user.getFontRevision());
    }
}
