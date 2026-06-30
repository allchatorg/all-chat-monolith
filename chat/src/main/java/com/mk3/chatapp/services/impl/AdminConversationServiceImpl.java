package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.MessageSearchRequestDTO;
import com.mk3.chatapp.dtos.responses.AdminConversationDTO;
import com.mk3.chatapp.dtos.responses.MessagePageDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ChatRoomRepository;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.AdminConversationService;
import com.mk3.chatapp.services.ChattingService;
import com.mk3.chatapp.services.MessagesService;
import com.mk3.chatapp.services.PrivateChatService;
import com.mk3.chatapp.services.SecurityService;
import com.mk3.chatapp.services.UserService;
import com.mk3.chatapp.specifications.MessageSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminConversationServiceImpl implements AdminConversationService {

    private final SecurityService securityService;
    private final UserService userService;
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final MessageRepository messageRepository;
    private final MessagesService messagesService;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final ChattingService chattingService;
    private final PrivateChatService privateChatService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminConversationDTO> listConversations(Long targetUserId, String search, int page, int pageSize) {
        User viewer = securityService.getCurrentUser();
        User target = userService.findById(targetUserId);

        // Only counterparts the viewer strictly outranks may be revealed. Pushing this set into
        // the query keeps pagination totals correct (no post-fetch filtering).
        Set<Role> allowedCounterpartRoles = Arrays.stream(Role.values())
                .filter(role -> viewer.getRole().canActOn(role))
                .collect(Collectors.toSet());

        Pageable pageable = PageRequest.of(page, pageSize);
        if (allowedCounterpartRoles.isEmpty()) {
            return Page.empty(pageable);
        }

        // Always bind a non-null string: Postgres cannot type a null bind inside lower()/concat()
        // (it would infer bytea). An empty term turns the LIKE into a match-all.
        String normalizedSearch = (search == null || search.isBlank()) ? "" : search.trim();
        Page<UserChatRoom> rows = userChatRoomRepository.findCounterpartConversationsForUser(
                targetUserId, allowedCounterpartRoles, normalizedSearch, pageable);

        return rows.map(row -> toAdminConversationDTO(viewer, target, row));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponseDTO> searchConversationMessages(Long targetUserId, MessageSearchRequestDTO request) {
        User viewer = securityService.getCurrentUser();
        User target = userService.findById(targetUserId);
        // Same membership + counterpart-visibility checks as getConversationMessages: the search is
        // scoped to the single selected conversation.
        ChatRoom room = loadPrivateRoomWithMember(target, request.chatRoomId());
        User counterpart = privateChatService.getCounterpart(target, room);
        assertCanViewConversation(viewer, target, counterpart);

        Pageable pageable = PageRequest.of(request.page(), request.size(), Sort.by(Sort.Direction.DESC, "id"));
        Page<Message> messages = messageRepository.findAll(
                MessageSpecification.getSpecification(room.getId(), request, viewer.getRole().isStaffMember()),
                pageable);
        return messages.map(message -> messageMapper.toMessageResponseDTO(message, viewer.getRole().isStaffMember()));
    }

    @Override
    @Transactional(readOnly = true)
    public MessagePageDTO getConversationMessages(Long targetUserId, Long roomId, Long afterMessageId,
                                                  Long beforeMessageId, Long aroundMessageId) {
        User viewer = securityService.getCurrentUser();
        User target = userService.findById(targetUserId);
        ChatRoom room = loadPrivateRoomWithMember(target, roomId);
        User counterpart = privateChatService.getCounterpart(target, room);
        assertCanViewConversation(viewer, target, counterpart);

        // Viewer is staff, so deleted (but not quarantined) messages are included.
        return messagesService.getPaginatedMessages(roomId, afterMessageId, beforeMessageId,
                aroundMessageId, viewer.getRole());
    }

    @Override
    @Transactional
    public void deleteConversationMessage(Long targetUserId, Long roomId, Long messageId) {
        User viewer = securityService.getCurrentUser();
        User target = userService.findById(targetUserId);
        ChatRoom room = loadPrivateRoomWithMember(target, roomId);
        User counterpart = privateChatService.getCounterpart(target, room);
        assertCanViewConversation(viewer, target, counterpart);

        Message message = messagesService.findById(messageId, viewer.getRole());
        if (message == null || !message.getChatRoom().getId().equals(roomId)) {
            throw new AccessDeniedException("Message is not part of this conversation");
        }
        // ChattingService.deleteMessage still enforces viewer.canActOn(sender) (or own message)
        // and writes the audit log + broadcasts the deletion to live participants.
        chattingService.deleteMessage(messageId);
    }

    private AdminConversationDTO toAdminConversationDTO(User viewer, User target, UserChatRoom counterpartRow) {
        ChatRoom room = counterpartRow.getChatRoom();
        User counterpart = counterpartRow.getUser();
        Message lastMessage = messagesService.getLastMessage(room.getId(), viewer.getRole());
        Long totalMessageCount = messagesService.getMessageCount(room.getId(), viewer.getRole());
        boolean blocked = target.getBlockedUsers().contains(counterpart)
                || counterpart.getBlockedUsers().contains(target);

        return new AdminConversationDTO(
                room.getId(),
                userMapper.toMinimalDto(target),
                userMapper.toMinimalDto(counterpart),
                lastMessage != null ? messageMapper.toMessageResponseDTO(lastMessage) : null,
                totalMessageCount,
                lastMessage != null ? lastMessage.getCreatedAt() : null,
                blocked
        );
    }

    private ChatRoom loadPrivateRoomWithMember(User target, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found: " + roomId));
        if (room.getType() != ChatRoomType.PRIVATE) {
            throw new IllegalArgumentException("Chat room is not private");
        }
        // The reviewed user must actually be a member of this room, otherwise an arbitrary roomId
        // could be probed under any profile.
        userChatRoomRepository.findUserChatRoomByUserAndChatRoom(target, room)
                .orElseThrow(() -> new AccessDeniedException("User is not part of this conversation"));
        return room;
    }

    private void assertCanViewConversation(User viewer, User target, User counterpart) {
        boolean canActOnTarget = viewer.getId().equals(target.getId())
                || viewer.getRole().canActOn(target.getRole());
        // Use Role.canActOn directly (strict level) for the counterpart — NOT canActOnTargetUser,
        // which short-circuits true for self/equal and would leak equal-rank conversations.
        boolean canActOnCounterpart = counterpart != null
                && viewer.getRole().canActOn(counterpart.getRole());
        if (!canActOnTarget || !canActOnCounterpart) {
            throw new AccessDeniedException("You cannot view this conversation");
        }
    }
}
