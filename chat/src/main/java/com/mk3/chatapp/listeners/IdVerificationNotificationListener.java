package com.mk3.chatapp.listeners;

import com.mk3.chatapp.dtos.responses.IdVerificationRequiredDTO;
import com.mk3.chatapp.dtos.responses.IdVerificationResultDTO;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.events.IdVerificationRequiredEvent;
import com.mk3.chatapp.events.IdVerificationResultEvent;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.services.MailSenderService;
import com.mk3.chatapp.services.UserService;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends id-verification notifications only after the status change is
 * committed — clients react to these events by refetching the current user,
 * so an in-transaction broadcast would let them read the stale status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdVerificationNotificationListener {

    private final UserService userService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final MailSenderService mailSenderService;

    @TransactionalEventListener
    public void onIdVerificationRequired(IdVerificationRequiredEvent event) {
        webSocketBroadcastService.broadcastToUser(event.userId(), WebSocketMessage.builder()
                .type(WebSocketMessageType.ID_VERIFICATION_REQUIRED)
                .data(new IdVerificationRequiredDTO(event.userId(), event.reportCaseId()))
                .build());

        try {
            mailSenderService.sendIdVerificationRequiredEmail(userService.findById(event.userId()));
        } catch (Exception e) {
            log.error("Failed to send identity verification required email to user {}", event.userId(), e);
        }
    }

    @TransactionalEventListener
    public void onIdVerificationResult(IdVerificationResultEvent event) {
        WebSocketMessage webSocketMessage = WebSocketMessage.builder()
                .type(WebSocketMessageType.ID_VERIFICATION_RESULT)
                .data(new IdVerificationResultDTO(event.userId(), event.reportCaseId(), event.passed()))
                .build();

        webSocketBroadcastService.broadcastToUser(event.userId(), webSocketMessage);
        webSocketBroadcastService.broadcastToUsers(userService.findStaffMembers(), webSocketMessage);
    }
}
