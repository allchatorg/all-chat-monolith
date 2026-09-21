package com.mk3.chatapp.services;

import com.mk3.chatapp.enums.FontPreset;
import com.mk3.chatapp.events.ProBadgeChangedEvent;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProFontLifecycleTest {
    private final UserRepository users = mock(UserRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ProFontService fonts = new ProFontService(users, entityManager);
    private final ProBadgeService badges = new ProBadgeService(users, events, entityManager, fonts);

    @Test
    void renewalBeforeSweepResetsElapsedChoicesWithoutBroadcastForHiddenBadge() {
        var user = customUser(Instant.now().minusSeconds(1));
        badges.updateEntitlement(user, Instant.now().plusSeconds(3600));
        assertThat(user.isProActive()).isTrue();
        assertThat(user.getUsernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(user.getMessageFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(user.getFontRevision()).isEqualTo(8);
        assertThat(user.getFontChangesCount()).isEqualTo(4);
        verifyNoInteractions(events);
    }

    @Test
    void continuousRenewalRetainsChoicesAndRevision() {
        var user = customUser(Instant.now().plusSeconds(100));
        badges.updateEntitlement(user, Instant.now().plusSeconds(3600));
        assertThat(user.getUsernameFont()).isEqualTo(FontPreset.INTER);
        assertThat(user.getFontRevision()).isEqualTo(7);
        verify(users, never()).updateFontState(anyLong(), any(), any(), anyLong(), any(), anyInt());
        verifyNoInteractions(events);
    }

    @Test
    void entitlementRevocationResetsChoicesImmediately() {
        var user = customUser(Instant.now().plusSeconds(100));
        badges.updateEntitlement(user, null);
        assertThat(user.getUsernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(user.getFontRevision()).isEqualTo(8);
        verifyNoInteractions(events);
    }

    @Test
    void hiddenBadgeSweepResetsFontsWithoutNewFanout() {
        var user = customUser(Instant.now().minusSeconds(1));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        badges.expireBadge(7L);
        assertThat(user.getUsernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(user.getFontRevision()).isEqualTo(8);
        verifyNoInteractions(events);
    }

    @Test
    void ordinaryBadgeEventContainsCurrentFontTuple() {
        var user = customUser(Instant.now().plusSeconds(100));
        user.setShowProBadge(true);
        badges.refreshVisibility(user);
        var captor = org.mockito.ArgumentCaptor.forClass(ProBadgeChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().badge().usernameFont()).isEqualTo(FontPreset.INTER);
        assertThat(captor.getValue().badge().messageFont()).isEqualTo(FontPreset.OPEN_SANS);
        assertThat(captor.getValue().badge().fontRevision()).isEqualTo(7);
    }

    @Test
    void batchLookupReturnsEffectiveDefaultsBeforeSweep() {
        var user = customUser(Instant.now().minusSeconds(1));
        when(users.findByIdIn(List.of(7L))).thenReturn(List.of(user));
        var result = badges.lookup(List.of(7L)).getFirst();
        assertThat(result.usernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(result.messageFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(result.fontRevision()).isEqualTo(7);
        assertThat(result.proBadgeVisible()).isFalse();
    }

    private static User customUser(Instant paidThrough) {
        return User.builder().id(7L).username("tester").proPaidThrough(paidThrough).showProBadge(false)
                .usernameFont(FontPreset.INTER).messageFont(FontPreset.OPEN_SANS).fontRevision(7)
                .fontChangesCount(4).build();
    }
}
