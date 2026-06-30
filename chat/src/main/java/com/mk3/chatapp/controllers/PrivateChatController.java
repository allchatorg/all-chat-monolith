package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.requests.CreatePrivateChatRequestDTO;
import com.mk3.chatapp.dtos.responses.PrivateChatDTO;
import com.mk3.chatapp.services.PrivateChatService;
import com.mk3.chatapp.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/private-chats")
@PreAuthorize("@security.isStaffMember()")
public class PrivateChatController {

    private final PrivateChatService privateChatService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<PrivateChatDTO> createOrGet(@Valid @RequestBody CreatePrivateChatRequestDTO request,
                                                      Principal connectedUser) {
        var user = userService.getPrincipal(connectedUser);
        return ResponseEntity.ok(privateChatService.getOrCreatePrivateChat(user, request.otherUserId()));
    }

    @GetMapping
    public ResponseEntity<List<PrivateChatDTO>> list(Principal connectedUser) {
        var user = userService.getPrincipal(connectedUser);
        return ResponseEntity.ok(privateChatService.listMyConversations(user));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<PrivateChatDTO> open(@PathVariable Long roomId, Principal connectedUser) {
        var user = userService.getPrincipal(connectedUser);
        return ResponseEntity.ok(privateChatService.openConversation(user, roomId));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> hide(@PathVariable Long roomId, Principal connectedUser) {
        var user = userService.getPrincipal(connectedUser);
        privateChatService.hideConversation(user, roomId);
        return ResponseEntity.noContent().build();
    }
}
