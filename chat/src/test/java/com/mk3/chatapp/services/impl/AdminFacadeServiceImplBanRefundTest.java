package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.BanRequestDTO;
import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.mappers.AuditLogCustomMapper;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.AuditLog;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UsernameHistoryRepository;
import com.mk3.chatapp.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFacadeServiceImplBanRefundTest {

    @Mock private BanService banService;
    @Mock private AdsModerationPort adsModerationPort;
    @Mock private UserService userService;
    @Mock private SecurityService securityService;
    @Mock private MessagesService messagesService;
    @Mock private WebSocketBroadcastService webSocketBroadcastService;
    @Mock private RoleManagementService roleManagementService;
    @Mock private SessionManagementService sessionManagementService;
    @Mock private UsernameHistoryRepository usernameHistoryRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ChatRoomService chatRoomService;
    @Mock private RoomActivityService roomActivityService;
    @Mock private UserMapper userMapper;
    @Mock private AuditLogCustomMapper auditLogCustomMapper;

    @InjectMocks private AdminFacadeServiceImpl service;

    private static BanRequestDTO banRequest(BanType banType, Duration duration) {
        return new BanRequestDTO(42L, duration, ReportType.SPAMMING_ADVERTISING_BEGGING, banType, "test ban", false, null, null);
    }

    private AuditLog stubBan(BanRequestDTO request) {
        User staff = new User();
        AuditLog auditLog = mock(AuditLog.class);
        when(securityService.getCurrentUser()).thenReturn(staff);
        when(banService.banUser(request, staff)).thenReturn(auditLog);
        return auditLog;
    }

    @Test
    void permanentBanTriggersRefundOfPendingAdPurchases() {
        BanRequestDTO request = banRequest(BanType.PERMANENT, null);
        AuditLog auditLog = stubBan(request);
        when(adsModerationPort.refundPendingAdPurchases(42L))
                .thenReturn(new AdsModerationPort.PendingAdRefundResult(1, 1, 20.0, "USD"));

        AuditLog result = service.banUser(request);

        assertThat(result).isSameAs(auditLog);
        verify(adsModerationPort).refundPendingAdPurchases(42L);
    }

    @Test
    void temporaryBanDoesNotTouchAdPurchases() {
        BanRequestDTO request = banRequest(BanType.TEMPORARY, Duration.ofHours(1));
        stubBan(request);

        service.banUser(request);

        verifyNoInteractions(adsModerationPort);
    }

    @Test
    void refundFailureDoesNotFailTheBan() {
        BanRequestDTO request = banRequest(BanType.PERMANENT, null);
        AuditLog auditLog = stubBan(request);
        when(adsModerationPort.refundPendingAdPurchases(anyLong()))
                .thenThrow(new RuntimeException("ads module down"));

        AuditLog result = service.banUser(request);

        assertThat(result).isSameAs(auditLog);
    }
}
