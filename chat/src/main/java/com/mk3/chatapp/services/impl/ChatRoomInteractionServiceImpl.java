package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.CreateChatRoomRequestDTO;
import com.mk3.chatapp.dtos.requests.MessageSearchRequestDTO;
import com.mk3.chatapp.dtos.requests.ReactionRequestDTO;
import com.mk3.chatapp.dtos.requests.ReportRequest;
import com.mk3.chatapp.dtos.responses.*;
import com.mk3.chatapp.enums.*;
import com.mk3.chatapp.exceptions.ConflictException;
import com.mk3.chatapp.mappers.ChatRoomMapper;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomInteractionServiceImpl implements ChatRoomInteractionService {
    private final ChatRoomService chatRoomService;
    private final UserChatRoomServiceImpl userChatRoomService;
    private final UserServiceImpl userService;
    private final RoomActivityService roomActivityService;
    private final MessagesService messagesService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final ReportManagerService reportManagerService;

    private final ChatRoomMapper chatRoomMapper;
    private final MessageMapper messageMapper;
    private final SecurityService securityService;
    private final ReactionService reactionService;
    private final PrivateChatService privateChatService;
    private final MessagePromotionPort messagePromotionPort;
    private final MessagePromotionEnrichmentService messagePromotionEnrichmentService;
    private final RoomPromotionPort roomPromotionPort;

    private static final int MAX_PROMOTED_ROOM_PAGES = 25;

    @Override
    @Transactional
    public UserChatRoomDTO joinChatRoomAndBroadcastEvent(Principal principal, Long chatRoomId) {
        var user = userService.getPrincipal(principal);
        var chatRoom = chatRoomService.findById(chatRoomId);

        return joinChatRoomAndBroadcastEvent(user, chatRoom);
    }

    @Override
    @Transactional
    public UserChatRoomDTO joinRandomChatRoomAndBroadcastEvent(Principal principal) {
        var user = userService.getPrincipal(principal);
        var chatRoom = chatRoomService.findRandomJoinableChatRoom(user.getRole(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No accessible chat rooms are available."));

        return joinChatRoomAndBroadcastEvent(user, chatRoom);
    }

    private UserChatRoomDTO joinChatRoomAndBroadcastEvent(User user, ChatRoom chatRoom) {
        assertPublicRoom(chatRoom);
        validateJoinChatRoom(user, chatRoom);

        var userChatRoom = userChatRoomService.joinChatRoom(user, chatRoom);

        var lastMessage = messageMapper
                .toMessageResponseDTO(messagesService.getLastMessage(chatRoom.getId(), user.getRole()));

        var roomPopulation = roomActivityService.userJoinedRoom(chatRoom.getId().toString(), user.getId().toString());

        if (userChatRoom == null) {
            throw new IllegalArgumentException("User is already part of the chat room: " + chatRoom.getName());
        }

        var webSocketMessage = WebSocketMessage.builder().type(WebSocketMessageType.POPULARITY_UPDATE)
                .chatRoomName(chatRoom.getName()).data(roomPopulation).build();

        webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);

        return new UserChatRoomDTO(userChatRoom.getId(), chatRoom.getName(), chatRoom.getRequiredAccessLevel(),
                chatRoom.getId(), roomPopulation, null, null, lastMessage);
    }

    @Override
    public void joinChatRoom(User user, String chatRoomName) {
        ChatRoom chatRoom = chatRoomService.findByName(chatRoomName);
        validateJoinChatRoom(user, chatRoom);
        userChatRoomService.joinChatRoom(user, chatRoom);
    }

    @Override
    public void leaveChatRoom(Principal principal, Long chatRoomId) {
        var user = userService.getPrincipal(principal);
        var chatRoom = chatRoomService.findById(chatRoomId);
        assertPublicRoom(chatRoom);
        userChatRoomService.leaveChatRoom(user, chatRoom);

        var roomPopulation = roomActivityService.userLeftRoom(chatRoom.getId().toString(), user.getId().toString());

        var webSocketMessage = WebSocketMessage.builder().type(WebSocketMessageType.POPULARITY_UPDATE)
                .chatRoomName(chatRoom.getName()).data(roomPopulation).build();

        webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);
    }

    @Override
    public RoomPopulationDTO userBecomesActiveInChatRoom(Principal connectedUser, Long previousActiveChatRoomId,
                                                         Long chatRoomId) {

        if (previousActiveChatRoomId != null) {
            userBecomesInactiveInChatRoom(connectedUser, previousActiveChatRoomId);
        }

        var user = userService.getPrincipal(connectedUser);
        var chatRoom = user.getUserChatRooms().stream().filter(ucr -> ucr.getChatRoom().getId().equals(chatRoomId))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("User is not part of the chat room with ID: " + chatRoomId))
                .getChatRoom();
        assertPublicRoom(chatRoom);

        var roomPopulation = roomActivityService.userBecomesActiveInRoom(chatRoom.getId().toString(),
                user.getId().toString());
        var webSocketMessage = WebSocketMessage.builder().type(WebSocketMessageType.POPULARITY_UPDATE)
                .chatRoomName(chatRoom.getName()).data(roomPopulation).build();

        webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);

        return roomPopulation;
    }

    private void userBecomesInactiveInChatRoom(Principal connectedUser, Long chatRoomId) {
        if (chatRoomId == null) {
            return;
        }

        var user = userService.getPrincipal(connectedUser);
        var chatRoom = user.getUserChatRooms().stream().filter(ucr -> ucr.getChatRoom().getId().equals(chatRoomId))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("User is not part of the chat room with ID: " + chatRoomId))
                .getChatRoom();

        var roomPopulation = roomActivityService.userBecomesInactiveInRoom(chatRoom.getId().toString(),
                user.getId().toString());
        var webSocketMessage = WebSocketMessage.builder().type(WebSocketMessageType.POPULARITY_UPDATE)
                .chatRoomName(chatRoom.getName()).data(roomPopulation).build();

        webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);
    }

    @Override
    @Transactional
    public UserChatRoomDTO createAndJoinChatRoom(Principal principal, CreateChatRoomRequestDTO request) {
        var user = userService.getPrincipal(principal);

        if (user.getRole().equals(Role.GUEST)) {
            throw new IllegalArgumentException("A guest user cannot create a room");
        }

        var chatRoom = chatRoomService.createChatRoom(request, user);
        UserChatRoom userChatroom = userChatRoomService.joinChatRoom(user, chatRoom);
        var roomPopulation = roomActivityService.userJoinedRoom(chatRoom.getId().toString(), user.getId().toString());

        // TODO replace with mapper
        return new UserChatRoomDTO(userChatroom.getId(), chatRoom.getName(), chatRoom.getRequiredAccessLevel(),
                chatRoom.getId(), roomPopulation, null, null, null);
    }

    @Override
    @Transactional
    public void disconnectUserFromChatRooms(User user) {
        var userChatRooms = userChatRoomService.findAllByUser(user);
        for (var userChatRoom : userChatRooms) {
            var chatRoom = userChatRoom.getChatRoom();
            var population = roomActivityService.userLeftRoom(chatRoom.getId().toString(), user.getId().toString());
            var webSocketMessage = WebSocketMessage.builder().type(WebSocketMessageType.POPULARITY_UPDATE)
                    .chatRoomName(chatRoom.getName()).data(population).build();

            webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);
        }
    }

    @Override
    @Transactional
    public void connectUserToChatRooms(User user) {
        var userChatRooms = userChatRoomService.findAllByUser(user);
        for (var userChatRoom : userChatRooms) {
            var chatRoom = userChatRoom.getChatRoom();
            var population = roomActivityService.userJoinedRoom(chatRoom.getId().toString(), user.getId().toString());

            var webSocketMessage = WebSocketMessage.builder().type(WebSocketMessageType.POPULARITY_UPDATE)
                    .chatRoomName(chatRoom.getName()).data(population).build();

            webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);
        }
    }

    @Override
    @Transactional
    public void handleHeartbeat(Principal principal, Long activeRoomId) {
        var user = userService.getPrincipal(principal);
        var userChatRooms = userChatRoomService.findAllByUser(user);

        List<String> allRoomIds = userChatRooms.stream()
                .map(ucr -> ucr.getChatRoom().getId().toString())
                .toList();

        String activeRoomIdStr = activeRoomId != null ? activeRoomId.toString() : null;

        roomActivityService.recordHeartbeat(user.getId().toString(), activeRoomIdStr, allRoomIds);

        // Broadcast updated population for the active room so all users see the
        // refreshed count
        if (activeRoomId != null) {
            var chatRoom = userChatRooms.stream()
                    .map(ucr -> ucr.getChatRoom())
                    .filter(cr -> cr.getId().equals(activeRoomId))
                    .findFirst()
                    .orElse(null);

            if (chatRoom != null) {
                var population = roomActivityService.getRoomPopulation(chatRoom.getId().toString());
                var webSocketMessage = WebSocketMessage.builder()
                        .type(WebSocketMessageType.POPULARITY_UPDATE)
                        .chatRoomName(chatRoom.getName())
                        .data(population)
                        .build();
                webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);
            }
        }
    }

    @Transactional
    @Override
    public List<UserChatRoomDTO> findAllUserChatRoomsWithPopulationAndLastMessage(User user) {

        return userChatRoomService.findAllByUserWithPopulation(user).stream().map(userChatRoom -> {
            var lastMessage = messagesService.getLastMessage(userChatRoom.chatRoomId(), user.getRole());
            var missedMessagesCount = getMissedMessagesCount(userChatRoom.chatRoomId(), user.getId());

            return new UserChatRoomDTO(userChatRoom.id(), userChatRoom.chatRoomName(),
                    userChatRoom.chatRoomRequiredAccessLevel(), userChatRoom.chatRoomId(),
                    userChatRoom.roomPopulation(), missedMessagesCount, userChatRoom.lastReadMessage(),
                    lastMessage != null ? messageMapper.toMessageResponseDTO(lastMessage) : null);
        }).collect(Collectors.toList());
    }

    @Override
    public ChatRoomDTO findById(Long chatRoomId) {
        var chatRoom = chatRoomService.findById(chatRoomId);

        return chatRoomMapper.toChatRoomDTO(chatRoom);
    }

    @Override
    public List<RoomPopulationDTO> searchChatRoomsByName(String name, Principal principal) {
        if (name == null || name.isBlank()) {
            return List.of();
        }

        var requesterRole = principal != null ? userService.getPrincipal(principal).getRole() : Role.GUEST;
        var chatRooms = chatRoomService.searchChatRoomsByName(name, requesterRole);
        return chatRooms.stream().map(chatRoom -> roomActivityService.getRoomPopulation(chatRoom.getId().toString()))
                .toList();
    }

    @Override
    public MessagePageDTO getMessages(Long roomId, Long afterMessageId, Long beforeMessageId, Long aroundMessageId,
                                      Principal principal) {

        var user = userService.getPrincipal(principal);
        var chatRoom = chatRoomService.findById(roomId);
        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            privateChatService.assertMember(user, chatRoom);
        }
        return messagesService.getPaginatedMessages(roomId, afterMessageId, beforeMessageId, aroundMessageId,
                user.getRole());
    }

    @Override
    public ChatRoomWithMessageMetadataDTO getChatRoomWithMessagesPage(Long roomId, Principal connectedUser) {
        var chatRoom = chatRoomService.findById(roomId);
        var user = userService.getPrincipal(connectedUser);

        var userChatRoom = userChatRoomService.findByUserAndChatRoom(user, chatRoom);
        Long lastReadMessageId = userChatRoom.getLastReadMessage() != null ? userChatRoom.getLastReadMessage().getId()
                : null;

        var messageCount = messagesService.getMessageCount(roomId, user.getRole());
        var messagesPage = messagesService.getPaginatedMessages(roomId, null, null, lastReadMessageId, user.getRole());

        return new ChatRoomWithMessageMetadataDTO(chatRoom.getId(), chatRoom.getName(), messagesPage.messages(),
                chatRoom.isArchived(), messageCount, messagesPage.hasPrevious(), messagesPage.hasNext(),
                messagesPage.firstMessageId(), messagesPage.lastMessageId(), lastReadMessageId);
    }

    @Override
    public Page<MessageResponseDTO> searchMessages(Long roomId, MessageSearchRequestDTO request, Principal principal) {
        var user = userService.getPrincipal(principal);
        var chatRoom = chatRoomService.findById(roomId);
        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            privateChatService.assertMember(user, chatRoom);
        }
        return messagesService.searchMessages(roomId, request, user.getRole());
    }

    @Override
    public MessageResponseDTO updateLastReadMessage(Principal connectedUser, Long roomId, Long messageId) {
        var user = userService.getPrincipal(connectedUser);
        var chatRoom = chatRoomService.findById(roomId);
        var message = messagesService.findById(messageId, user.getRole());

        if (!Objects.equals(message.getChatRoom().getId(), chatRoom.getId())) {
            throw new IllegalArgumentException("Message does not belong to the chat room with ID: " + chatRoom.getId());
        }

        userChatRoomService.updateLastReadMessage(user, chatRoom, message);

        return messageMapper.toMessageResponseDTO(message);
    }

    @Override
    public void reportMessage(ReportRequest request) {
        reportManagerService.createReportForMessage(request.messageId(), request.reportType(), request.description());
    }

    @Override
    public void reactToMessage(ReactionRequestDTO reactionRequestDTO, ReactionType reactionType) {
        var currentUser = securityService.getCurrentUser();
        var message = messagesService.findById(reactionRequestDTO.messageId(), currentUser.getRole());
        if (message.getDeleted()) {
            throw new IllegalArgumentException("Cannot react to a deleted message.");
        }

        chatRoomService.validateRoomIsNotArchived(message.getChatRoom(), "react to messages");

        var roomId = message.getChatRoom().getId();

        if (reactionType == ReactionType.ADD) {
            reactionService.addReaction(currentUser, message, reactionRequestDTO.emoji(), reactionRequestDTO.emojiId());
            roomActivityService.incrementReactionCount(roomId, message.getId());
        } else if (reactionType == ReactionType.REMOVE) {
            reactionService.removeReaction(currentUser, message, reactionRequestDTO.emoji(),
                    reactionRequestDTO.emojiId());
            roomActivityService.decrementReactionCount(roomId, message.getId());
        } else {
            throw new IllegalArgumentException("Unsupported reaction type: " + reactionType);
        }
    }

    @Override
    public ReactionDetailsDTO getMessageEmojiReactions(Long messageId, String emoji, Integer limit) {
        return reactionService.findByMessageIdAndEmoji(messageId, emoji, limit);
    }

    @Override
    public Page<RoomPopulationDTO> getChatRoomLeaderboard(int page, int pageSize, RoomPopularitySort popularitySort,
                                                          ChatRoomNoiseLevelEnum chatRoomNoiseLevel) {
        List<String> roomsToExclude = chatRoomService.getAllSpecialChatRoomNames().stream().map(String::toLowerCase)
                .toList();

        return roomActivityService.getChatRoomLeaderboard(page, pageSize, roomsToExclude, popularitySort,
                chatRoomNoiseLevel);
    }

    @Override
    public Page<MessageResponseDTO> getTopReactedMessages(Long roomId, int page, int pageSize,
                                                          TopReactedPeriod period, Principal principal) {
        var user = userService.getPrincipal(principal);
        var messagePage = roomActivityService.getTopReactedMessages(roomId, page, pageSize, period);

        var messages = messagePage.getContent().stream().map(dto -> {
            try {
                var message = messagesService.findById(dto.messageId(), user.getRole());
                return messageMapper.toMessageResponseDTO(message);
            } catch (Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).toList();

        messages = messagePromotionEnrichmentService.enrich(messages);

        return new org.springframework.data.domain.PageImpl<>(messages, messagePage.getPageable(),
                messagePage.getTotalElements());
    }

    @Override
    public Page<MessageResponseDTO> getPromotedMessages(Long roomId, int page, int pageSize, Principal principal) {
        var user = userService.getPrincipal(principal);
        var idPage = messagePromotionPort.getApprovedPromotedMessageIds(roomId, page, pageSize);

        // Load one by one in the port's approvedAt-desc order (mirrors top-reacted)
        var messages = idPage.getContent().stream().map(messageId -> {
            try {
                var message = messagesService.findById(messageId, user.getRole());
                return messageMapper.toMessageResponseDTO(message);
            } catch (Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).toList();

        messages = messagePromotionEnrichmentService.enrich(messages);

        return new org.springframework.data.domain.PageImpl<>(messages, idPage.getPageable(),
                idPage.getTotalElements());
    }

    @Override
    public Page<PromotedRoomDTO> getPromotedRooms(int page, int pageSize) {
        // Approved promotions stay eligible, but the public list is capped at
        // 25 pages: clamp the index and reported total so the client never asks
        // for page 26.
        int safePage = Math.max(0, Math.min(page, MAX_PROMOTED_ROOM_PAGES - 1));
        int safeSize = pageSize <= 0 ? 8 : Math.min(pageSize, 100);
        var rowPage = roomPromotionPort.getPromotedRooms(safePage, safeSize);
        var rows = rowPage.getContent().stream()
                .map(r -> PromotedRoomDTO.from(roomActivityService.getRoomPopulation(r.roomId().toString()),
                        r.promotedAt()))
                .toList();
        long cappedTotal = Math.min(rowPage.getTotalElements(), (long) MAX_PROMOTED_ROOM_PAGES * safeSize);
        return new org.springframework.data.domain.PageImpl<>(rows,
                org.springframework.data.domain.PageRequest.of(safePage, safeSize), cappedTotal);
    }

    private Integer getMissedMessagesCount(Long chatRoomId, Long userId) {
        var chatRoom = chatRoomService.findById(chatRoomId);
        var user = userService.findById(userId);

        var lastReadMessage = userChatRoomService.findByUserAndChatRoom(user, chatRoom).getLastReadMessage();

        if (lastReadMessage == null) {
            return 0;
        }

        var lastMessage = messagesService.getLastMessage(chatRoomId, user.getRole());

        if (lastMessage == null || lastReadMessage.getId() >= lastMessage.getId()) {
            return 0;
        }

        return messagesService.countByChatRoomIdAndIdGreaterThan(chatRoomId, lastReadMessage.getId(), user.getRole());
    }

    private void validateJoinChatRoom(User user, ChatRoom chatRoom) {
        chatRoomService.validateUserCanJoinChatRoom(user, chatRoom);
    }

    private void assertPublicRoom(ChatRoom chatRoom) {
        if (chatRoom != null && chatRoom.getType() == ChatRoomType.PRIVATE) {
            throw new ConflictException("Private chat rooms are not accessible via this endpoint");
        }
    }
}
