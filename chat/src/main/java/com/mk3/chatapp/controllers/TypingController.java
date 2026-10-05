package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.requests.TypingRequestDTO;
import com.mk3.chatapp.services.TypingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class TypingController {
    private final TypingService typingService;

    @MessageMapping("/chat.typing")
    public void typing(@Valid @Payload TypingRequestDTO request, Principal principal, StompHeaderAccessor headers) {
        if (principal == null) return;
        typingService.update(headers.getSessionId(), Long.valueOf(principal.getName()), request.chatRoomId(), request.typing());
    }

    @MessageExceptionHandler(Exception.class)
    public void ignoreInvalidTyping() {
        // Best-effort presence: malformed/unauthorized frames do not disrupt the chat connection.
    }
}
