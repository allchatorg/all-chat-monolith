package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.UpdateFontSettingsRequest;
import com.mk3.chatapp.dtos.responses.FontSettingsDTO;
import com.mk3.chatapp.enums.FontPreset;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class ProFontService {
    public static final int DAILY_LIMIT = 5;
    private final UserRepository users;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public ProFontService(UserRepository users, EntityManager entityManager) {
        this(users, entityManager, Clock.systemUTC());
    }

    ProFontService(UserRepository users, EntityManager entityManager, Clock clock) {
        this.users = users;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FontSettingsDTO getSettings(Long userId) {
        User user = users.findById(userId).orElseThrow(ProFontService::accountNotFound);
        entityManager.refresh(user);
        return toDto(user, clock.instant());
    }

    @Transactional
    public FontSettingsDTO updateSettings(Long userId, UpdateFontSettingsRequest request) {
        if (request == null || request.usernameFont() == null || request.messageFont() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose both font presets");
        }
        User user = users.findByIdForUpdate(userId).orElseThrow(ProFontService::accountNotFound);
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        Instant now = clock.instant();
        if (!user.isProActiveAt(now)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active allchat Pro subscription is required to change fonts");
        }
        // A retry of an already-saved selection remains free, even at the quota.
        if (user.getUsernameFont() == request.usernameFont() && user.getMessageFont() == request.messageFont()) {
            return toDto(user, now);
        }
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        int used = today.equals(user.getFontChangesDate()) ? user.getFontChangesCount() : 0;
        if (used >= DAILY_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You can save font changes 5 times per day. Your limit resets at midnight UTC.");
        }
        user.setUsernameFont(request.usernameFont());
        user.setMessageFont(request.messageFont());
        user.setFontRevision(user.getFontRevision() + 1);
        user.setFontChangesDate(today);
        user.setFontChangesCount(used + 1);
        persist(user);
        return toDto(user, now);
    }

    /** Caller holds the user row lock. Reset before replacing an expired paid-through date. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resetExpiredPreferences(User user) {
        if (user.isProActiveAt(clock.instant()) ||
                (user.getUsernameFont() == FontPreset.DEFAULT && user.getMessageFont() == FontPreset.DEFAULT)) return;
        user.setUsernameFont(FontPreset.DEFAULT);
        user.setMessageFont(FontPreset.DEFAULT);
        user.setFontRevision(user.getFontRevision() + 1);
        // Automatic expiry never spends or restores the user's daily allowance.
        persist(user);
    }

    private void persist(User user) {
        users.updateFontState(user.getId(), user.getUsernameFont(), user.getMessageFont(), user.getFontRevision(),
                user.getFontChangesDate(), user.getFontChangesCount());
    }

    private FontSettingsDTO toDto(User user, Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        int used = today.equals(user.getFontChangesDate()) ? user.getFontChangesCount() : 0;
        boolean active = user.isProActiveAt(now);
        return new FontSettingsDTO(active ? user.getUsernameFont() : FontPreset.DEFAULT,
                active ? user.getMessageFont() : FontPreset.DEFAULT, user.getFontRevision(), active, DAILY_LIMIT,
                Math.max(0, DAILY_LIMIT - used), today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static ResponseStatusException accountNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
}
