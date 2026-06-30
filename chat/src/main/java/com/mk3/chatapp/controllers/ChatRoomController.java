package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.*;
import com.mk3.chatapp.enums.ChatRoomNoiseLevelEnum;
import com.mk3.chatapp.enums.ReactionType;
import com.mk3.chatapp.enums.RoomPopularitySort;
import com.mk3.chatapp.services.ChatRoomInteractionService;
import com.mk3.chatapp.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat-rooms")
public class ChatRoomController {
    private final ChatRoomInteractionService chatRoomInteractionService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<RoomPopulationDTO>> getAllChatRooms(@RequestParam String name,
                                                                   Principal connectedUser) {

        var chatRooms = chatRoomInteractionService.searchChatRoomsByName(name, connectedUser);

        return ResponseEntity.ok(chatRooms);
    }

    @PostMapping("/random/join")
    public ResponseEntity<UserChatRoomDTO> joinRandomChatRoom(Principal connectedUser) {
        var joinedChatRoom = chatRoomInteractionService.joinRandomChatRoomAndBroadcastEvent(connectedUser);
        return ResponseEntity.ok(joinedChatRoom);
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<UserChatRoomDTO> joinChatRoom(@PathVariable Long roomId, Principal connectedUser) {
        var joinedChatRoom = chatRoomInteractionService.joinChatRoomAndBroadcastEvent(connectedUser, roomId);
        return ResponseEntity.ok(joinedChatRoom);
    }

    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveChatRoom(@PathVariable Long roomId, Principal connectedUser) {
        chatRoomInteractionService.leaveChatRoom(connectedUser, roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<UserChatRoomDTO> createAndJoinChatRoom(@Valid @RequestBody CreateChatRoomRequestDTO request,
                                                                 Principal connectedUser) {
        return ResponseEntity.ok(chatRoomInteractionService.createAndJoinChatRoom(connectedUser, request));
    }

    @GetMapping("/me")
    public ResponseEntity<List<UserChatRoomDTO>> getAllUserChatRooms(Principal connectedUser) {
        var user = userService.getPrincipal(connectedUser);
        chatRoomInteractionService.connectUserToChatRooms(user);
        var userChatRooms = chatRoomInteractionService.findAllUserChatRoomsWithPopulationAndLastMessage(user);
        return ResponseEntity.ok(userChatRooms);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ChatRoomWithMessageMetadataDTO> getChatRoomDetails(@PathVariable Long roomId,
                                                                             Principal connectedUser) {
        return ResponseEntity.ok(chatRoomInteractionService.getChatRoomWithMessagesPage(roomId, connectedUser));
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<MessagePageDTO> getMessages(Principal principal,
                                                      @PathVariable Long roomId,
                                                      @RequestParam(required = false) Long afterMessageId,
                                                      @RequestParam(required = false) Long beforeMessageId,
                                                      @RequestParam(required = false) Long aroundMessageId) {
        return ResponseEntity.ok(chatRoomInteractionService.getMessages(roomId, afterMessageId, beforeMessageId,
                aroundMessageId, principal));
    }

    @GetMapping("/{roomId}/messages/top-reacted")
    public ResponseEntity<Page<MessageResponseDTO>> getTopReactedMessages(@PathVariable Long roomId,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int pageSize,
                                                                          Principal connectedUser) {
        return ResponseEntity
                .ok(chatRoomInteractionService.getTopReactedMessages(roomId, page, pageSize, connectedUser));
    }

    @GetMapping("/messages/{messageId}/reactions/{emoji}")
    public ResponseEntity<ReactionDetailsDTO> getReactionsByEmoji(
            @PathVariable Long messageId,
            @PathVariable String emoji,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(chatRoomInteractionService.getMessageEmojiReactions(messageId, emoji, limit));
    }

    @PatchMapping("/{roomId}/messages/{messageId}/acknowledge")
    public ResponseEntity<MessageResponseDTO> updateLastReadMessage(@PathVariable Long roomId,
                                                                    @PathVariable Long messageId, Principal connectedUser) {
        return ResponseEntity.ok(chatRoomInteractionService.updateLastReadMessage(connectedUser, roomId, messageId));
    }

    @PostMapping("/{roomId}/messages/search")
    public ResponseEntity<Page<MessageResponseDTO>> searchMessages(Principal principal, @PathVariable Long roomId,
                                                                   @RequestBody MessageSearchRequestDTO request) {
        return ResponseEntity.ok(chatRoomInteractionService.searchMessages(roomId, request, principal));
    }

    @PostMapping("/report/message")
    public ResponseEntity<Void> reportMessage(@Valid @RequestBody ReportRequest request) {
        chatRoomInteractionService.reportMessage(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody HeartbeatRequestDTO heartbeatRequest, Principal connectedUser) {
        chatRoomInteractionService.handleHeartbeat(connectedUser, heartbeatRequest.activeRoomId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{roomId}/active")
    public ResponseEntity<RoomPopulationDTO> setActiveChatRoom(@PathVariable Long roomId,
                                                               @RequestParam(required = false) Long previousActiveRoomId, Principal connectedUser) {
        RoomPopulationDTO roomPopulation = chatRoomInteractionService.userBecomesActiveInChatRoom(connectedUser,
                previousActiveRoomId, roomId);
        return ResponseEntity.ok(roomPopulation);
    }

    @PatchMapping("/message-reactions")
    public ResponseEntity<Void> reactToMessage(@RequestBody ReactionRequestDTO reactionRequestDTO) {
        chatRoomInteractionService.reactToMessage(reactionRequestDTO, ReactionType.ADD);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/message-reactions")
    public ResponseEntity<Void> deleteReaction(@RequestBody ReactionRequestDTO reactionRequestDTO) {
        chatRoomInteractionService.reactToMessage(reactionRequestDTO, ReactionType.REMOVE);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/chatroom-leaderboard")
    public ResponseEntity<Page<RoomPopulationDTO>> getTopActiveRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) RoomPopularitySort popularitySort,
            @RequestParam(required = false) ChatRoomNoiseLevelEnum chatRoomNoiseLevel) {

        Page<RoomPopulationDTO> response = chatRoomInteractionService.getChatRoomLeaderboard(page, pageSize,
                popularitySort, chatRoomNoiseLevel);
        return ResponseEntity.ok(response);
    }
}
