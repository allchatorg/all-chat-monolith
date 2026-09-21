package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.UpdateFontSettingsRequest;
import com.mk3.chatapp.enums.FontPreset;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProFontServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T23:59:59Z");
    private final UserRepository users = mock(UserRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ProFontService service = new ProFontService(users, entityManager, Clock.fixed(NOW, ZoneOffset.UTC));
    private User user;

    @BeforeEach
    void setup() {
        user = User.builder().id(7L).username("tester").proPaidThrough(NOW.plusSeconds(3600)).build();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void bothSelectionsConsumeOneSaveAndHiddenBadgeDoesNotDenyEntitlement() {
        user.setShowProBadge(false);
        var result = service.updateSettings(7L, request(FontPreset.INTER, FontPreset.OPEN_SANS));
        assertThat(result.usernameFont()).isEqualTo(FontPreset.INTER);
        assertThat(result.messageFont()).isEqualTo(FontPreset.OPEN_SANS);
        assertThat(result.fontRevision()).isEqualTo(1);
        assertThat(result.changesRemaining()).isEqualTo(4);
        assertThat(result.resetsAt()).isEqualTo(Instant.parse("2026-09-22T00:00:00Z"));
        verify(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        verify(users).updateFontState(7L, FontPreset.INTER, FontPreset.OPEN_SANS, 1L, LocalDate.parse("2026-09-21"), 1);
    }

    @Test
    void fifthChangedSaveSucceedsSixthFailsButIdenticalRetryIsFree() {
        for (int index = 0; index < 5; index++) {
            service.updateSettings(7L, request(index % 2 == 0 ? FontPreset.INTER : FontPreset.DEFAULT, FontPreset.DEFAULT));
        }
        assertThat(service.updateSettings(7L, request(FontPreset.INTER, FontPreset.DEFAULT)).changesRemaining()).isZero();
        assertStatus(429, () -> service.updateSettings(7L, request(FontPreset.OPEN_SANS, FontPreset.DEFAULT)));
        assertThat(user.getFontRevision()).isEqualTo(5);
        assertThat(user.getFontChangesCount()).isEqualTo(5);
        verify(users, times(5)).updateFontState(anyLong(), any(), any(), anyLong(), any(), anyInt());
    }

    @Test
    void midnightUtcStartsNewAllowanceWithoutChangingAccountTimezone() {
        user.setFontChangesDate(LocalDate.parse("2026-09-21"));
        user.setFontChangesCount(5);
        user.setTimeZone("Pacific/Honolulu");
        var afterMidnight = new ProFontService(users, entityManager,
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC));
        var result = afterMidnight.updateSettings(7L, request(FontPreset.INTER, FontPreset.DEFAULT));
        assertThat(result.changesRemaining()).isEqualTo(4);
        assertThat(user.getFontChangesDate()).isEqualTo(LocalDate.parse("2026-09-22"));
        assertThat(result.resetsAt()).isEqualTo(Instant.parse("2026-09-23T00:00:00Z"));
    }

    @Test
    void expiredAndDeletedAccountsCannotSaveEvenDefaults() {
        user.setProPaidThrough(NOW);
        assertStatus(403, () -> service.updateSettings(7L, request(FontPreset.DEFAULT, FontPreset.DEFAULT)));
        user.setProPaidThrough(NOW.plusSeconds(100));
        user.setDeleted(true);
        assertStatus(403, () -> service.updateSettings(7L, request(FontPreset.INTER, FontPreset.DEFAULT)));
        verify(users, never()).updateFontState(anyLong(), any(), any(), anyLong(), any(), anyInt());
    }

    @Test
    void lockRefreshReplacesStaleEntitlementBeforeAuthorization() {
        doAnswer(call -> { user.setProPaidThrough(NOW); return null; })
                .when(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        assertStatus(403, () -> service.updateSettings(7L, request(FontPreset.INTER, FontPreset.DEFAULT)));
        verify(users, never()).updateFontState(anyLong(), any(), any(), anyLong(), any(), anyInt());
    }

    @Test
    void lockRefreshObservesLastAvailableSaveConsumedByAnotherRequest() {
        user.setFontChangesDate(LocalDate.parse("2026-09-21"));
        user.setFontChangesCount(4);
        doAnswer(call -> { user.setFontChangesCount(5); return null; })
                .when(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        assertStatus(429, () -> service.updateSettings(7L, request(FontPreset.INTER, FontPreset.DEFAULT)));
        verify(users, never()).updateFontState(anyLong(), any(), any(), anyLong(), any(), anyInt());
    }

    @Test
    void expiredGetReturnsDefaultImmediatelyBeforeSweepWithoutChangingQuota() {
        user.setUsernameFont(FontPreset.INTER);
        user.setMessageFont(FontPreset.OPEN_SANS);
        user.setFontRevision(3);
        user.setProPaidThrough(NOW);
        user.setFontChangesDate(LocalDate.parse("2026-09-21"));
        user.setFontChangesCount(3);
        var result = service.getSettings(7L);
        assertThat(result.usernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(result.messageFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(result.proActive()).isFalse();
        assertThat(result.fontRevision()).isEqualTo(3);
        assertThat(result.changesRemaining()).isEqualTo(2);
        verify(users, never()).updateFontState(anyLong(), any(), any(), anyLong(), any(), anyInt());
    }

    @Test
    void expiryResetIsIdempotentAndDoesNotRestoreOrSpendQuota() {
        user.setUsernameFont(FontPreset.INTER);
        user.setFontRevision(5);
        user.setProPaidThrough(NOW);
        user.setFontChangesDate(LocalDate.parse("2026-09-21"));
        user.setFontChangesCount(5);
        service.resetExpiredPreferences(user);
        service.resetExpiredPreferences(user);
        assertThat(user.getUsernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(user.getMessageFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(user.getFontRevision()).isEqualTo(6);
        assertThat(user.getFontChangesCount()).isEqualTo(5);
        verify(users).updateFontState(7L, FontPreset.DEFAULT, FontPreset.DEFAULT, 6L, LocalDate.parse("2026-09-21"), 5);
    }

    @Test
    void missingPresetsAreRejectedWithoutSpendingAllowance() {
        assertStatus(400, () -> service.updateSettings(7L, request(null, FontPreset.DEFAULT)));
        verify(users, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void legacyNullPresetsNormalizeAndAllFontColumnsResistOrdinaryUserSaves() throws Exception {
        user.setUsernameFont(null);
        user.setMessageFont(null);
        assertThat(user.getUsernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(user.getMessageFont()).isEqualTo(FontPreset.DEFAULT);
        for (String field : new String[]{"usernameFont", "messageFont", "fontRevision", "fontChangesDate", "fontChangesCount"}) {
            assertThat(User.class.getDeclaredField(field).getAnnotation(Column.class).updatable()).isFalse();
        }
    }

    private static UpdateFontSettingsRequest request(FontPreset username, FontPreset message) {
        return new UpdateFontSettingsRequest(username, message);
    }

    private static void assertStatus(int status, ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(status));
    }
}
