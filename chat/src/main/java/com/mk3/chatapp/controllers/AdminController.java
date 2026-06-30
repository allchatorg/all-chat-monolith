package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.dtos.WarnUserRequestDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.AdminConversationDTO;
import com.mk3.chatapp.dtos.responses.AuditLogDTO;
import com.mk3.chatapp.dtos.responses.MessagePageDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.dtos.responses.UserAdminViewDTO;
import com.mk3.chatapp.dtos.responses.UserDTO;
import com.mk3.chatapp.services.AdminConversationService;
import com.mk3.chatapp.services.AdminFacadeService;
import com.mk3.chatapp.services.AdsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.FRONT_END.URL}", allowCredentials = "true")
public class AdminController {
    private final AdminFacadeService adminFacadeService;
    private final AdminConversationService adminConversationService;
    private final AdsService adsService;

    @PostMapping("/bans")
    @PreAuthorize("@security.canActOnTargetUser(#banRequestDTO.userId())")
    public ResponseEntity<Void> banUser(@RequestBody BanRequestDTO banRequestDTO) {
        adminFacadeService.banUser(banRequestDTO);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/bans")
    public ResponseEntity<Page<BanResponseDTO>> searchActiveBans(
            @RequestParam(required = false) String userNameOrId,
            @RequestParam int page,
            @RequestParam int pageSize) {
        return ResponseEntity.ok(adminFacadeService.findActiveBans(userNameOrId, page, pageSize));
    }

    @DeleteMapping("/bans/{userId}")
    public ResponseEntity<Void> revokeBan(@PathVariable Long userId) {
        adminFacadeService.revokeBan(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{userId}/details")
    @PreAuthorize("@security.canActOnTargetUser(#userId)")
    public ResponseEntity<UserAdminViewDTO> getUserAdminViewDetails(@PathVariable Long userId) {
        UserAdminViewDTO userDetails = adminFacadeService.getUserAdminViewDetails(userId);
        return ResponseEntity.ok(userDetails);
    }

    @PostMapping("/users")
    public ResponseEntity<Page<UserDTO>> searchUsers(@RequestBody UserSearchRequestDTO request) {
        Page<UserDTO> users = adminFacadeService.searchUsers(request);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("@security.canActOnTargetUser(#userId)")
    public ResponseEntity<UserDTO> getUserAdminDetails(@PathVariable Long userId) {
        UserDTO userAdminDetails = adminFacadeService.getUserAdminDetails(userId);
        return ResponseEntity.ok(userAdminDetails);
    }

    @PostMapping("/user-messages")
    public ResponseEntity<Page<MessageResponseDTO>> getUserChatRoomMessages(
            @RequestBody MessageSearchRequestDTO request) {
        Page<MessageResponseDTO> messages = adminFacadeService.getUserMessages(request);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/users/{userId}/conversations")
    @PreAuthorize("@security.canActOnTargetUser(#userId)")
    public ResponseEntity<Page<AdminConversationDTO>> getUserConversations(
            @PathVariable Long userId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(adminConversationService.listConversations(userId, search, page, pageSize));
    }

    @PostMapping("/users/{userId}/conversations/messages/search")
    @PreAuthorize("@security.canActOnTargetUser(#userId)")
    public ResponseEntity<Page<MessageResponseDTO>> searchUserConversationMessages(
            @PathVariable Long userId,
            @RequestBody MessageSearchRequestDTO request) {
        return ResponseEntity.ok(adminConversationService.searchConversationMessages(userId, request));
    }

    @GetMapping("/users/{userId}/conversations/{roomId}/messages")
    @PreAuthorize("@security.canActOnTargetUser(#userId)")
    public ResponseEntity<MessagePageDTO> getUserConversationMessages(
            @PathVariable Long userId,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long afterMessageId,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(required = false) Long aroundMessageId) {
        return ResponseEntity.ok(adminConversationService.getConversationMessages(
                userId, roomId, afterMessageId, beforeMessageId, aroundMessageId));
    }

    @DeleteMapping("/users/{userId}/conversations/{roomId}/messages/{messageId}")
    @PreAuthorize("@security.canActOnTargetUser(#userId)")
    public ResponseEntity<Void> deleteUserConversationMessage(
            @PathVariable Long userId,
            @PathVariable Long roomId,
            @PathVariable Long messageId) {
        adminConversationService.deleteConversationMessage(userId, roomId, messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/warnings")
    public ResponseEntity<Void> warnUser(@RequestBody WarnUserRequestDTO warnRequestDTO) {
        adminFacadeService.warnUser(warnRequestDTO);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/role")
    public ResponseEntity<Void> updateUserRole(@RequestBody RoleUpdateRequest request) {
        adminFacadeService.updateUserRole(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(AuditLogSearchRequestDTO request) {
        Page<AuditLogDTO> auditLogs = adminFacadeService.searchAuditLogs(request);
        return ResponseEntity.ok(auditLogs);
    }

    @PutMapping("/chat-rooms/{roomId}/archive")
    public ResponseEntity<Void> archiveChatRoom(@PathVariable Long roomId) {
        adminFacadeService.archiveChatRoom(roomId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/chat-rooms/{roomId}/unarchive")
    public ResponseEntity<Void> unarchiveChatRoom(@PathVariable Long roomId) {
        adminFacadeService.unarchiveChatRoom(roomId);
        return ResponseEntity.ok().build();
    }
}
