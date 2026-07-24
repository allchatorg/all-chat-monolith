package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.requests.CreateMessageRequestDTO;
import com.mk3.chatapp.dtos.requests.MessageSearchRequestDTO;
import com.mk3.chatapp.dtos.responses.MessageEditHistoryDTO;
import com.mk3.chatapp.dtos.responses.MessagePageDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.mappers.MessageEditHistoryMapper;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.models.*;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.*;
import com.mk3.chatapp.specifications.MessageSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessagesServiceImpl implements MessagesService {
    public static final int MAX_LENGTH = 500;
    private final MessageRepository messageRepository;

    private final WebSocketBroadcastService webSocketBroadcastService;
    private final MessageMapper messageMapper;
    private final RoomActivityService roomActivityService;
    private final MessageEditHistoryService messageEditHistoryService;
    private final MessageEditHistoryMapper messageEditHistoryMapper;
    private final AttachmentService attachmentService;
    private final ChatRoomService chatRoomService;
    private final UserChatRoomRepository userChatRoomRepository;
    private final MessagePromotionEnrichmentService messagePromotionEnrichmentService;

    /**
     * Validates message content when saving a new message.
     * Content cannot be null or empty.
     */
    public static void validateMessageForSave(String content, int maxLength, List<AttachmentDTO> attachments) {
        if (content == null || content.isEmpty() && attachments.isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }

        if (content.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Message content exceeds maximum length of " + maxLength + " characters");
        }

        if (content.toLowerCase().contains(".onion")) {
            throw new IllegalArgumentException("Messages cannot contain .onion links");
        }
    }

    /**
     * Validates message content when editing an existing message.
     * Content can be empty if there are attachments.
     */
    public static void validateMessageForEdit(String content, int maxLength, int attachmentCount) {
        // If there are no attachments, content cannot be empty
        if (attachmentCount == 0 && (content == null || content.isEmpty())) {
            throw new IllegalArgumentException("Message content cannot be null or empty when there are no attachments");
        }

        // If content is provided, validate length and content rules
        if (content != null && !content.isEmpty()) {
            if (content.length() > maxLength) {
                throw new IllegalArgumentException(
                        "Message content exceeds maximum length of " + maxLength + " characters");
            }

            if (content.toLowerCase().contains(".onion")) {
                throw new IllegalArgumentException("Messages cannot contain .onion links");
            }
        }
    }

    public static MessageResponseDTO filterAttachments(MessageResponseDTO messageResponseDTO, List<Long> idsToRemove) {
        if (messageResponseDTO == null || messageResponseDTO.attachments() == null || idsToRemove == null
                || idsToRemove.isEmpty()) {
            return messageResponseDTO;
        }

        List<AttachmentDTO> filteredAttachments = messageResponseDTO.attachments().stream()
                .filter(attachment -> !idsToRemove.contains(attachment.id()))
                .toList();

        return new MessageResponseDTO(
                messageResponseDTO.id(),
                messageResponseDTO.content(),
                messageResponseDTO.chatRoomId(),
                messageResponseDTO.chatRoomName(),
                messageResponseDTO.senderId(),
                messageResponseDTO.senderUsername(),
                messageResponseDTO.senderRole(),
                messageResponseDTO.senderCountryCode(),
                messageResponseDTO.senderIdVerificationStatus(),
                messageResponseDTO.bannedUser(),
                messageResponseDTO.deleted(),
                messageResponseDTO.createdAt(),
                messageResponseDTO.editedAt(),
                messageResponseDTO.color(),
                filteredAttachments,
                messageResponseDTO.reactions(),
                messageResponseDTO.replyTo(),
                messageResponseDTO.promotion());
    }

    @Override
    public MessageResponseDTO broadcastMessage(Message message, Principal connectedUser) {

        var chatRoom = message.getChatRoom();
        var messageDTO = messageMapper.toMessageResponseDTO(message);

        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            var webSocketMessage = WebSocketMessage.builder()
                    .type(WebSocketMessageType.PRIVATE_NEW_MESSAGE)
                    .chatRoomName(null)
                    .data(messageDTO)
                    .build();
            sendToPrivateMembers(chatRoom.getId(), webSocketMessage);
            return messageDTO;
        }

        var webSocketMessage = WebSocketMessage.builder()
                .type(WebSocketMessageType.NEW_MESSAGE)
                .chatRoomName(chatRoom.getName())
                .data(messageDTO)
                .build();

        webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);
        return messageDTO;
    }

    private void sendToPrivateMembers(Long chatRoomId, WebSocketMessage payload) {
        List<UserChatRoom> members = userChatRoomRepository.findByChatRoom(
                chatRoomService.findById(chatRoomId));
        for (UserChatRoom member : members) {
            webSocketBroadcastService.sendPrivateToUser(member.getUser().getId(), payload);
        }
    }

    @Override
    public Message saveMessage(CreateMessageRequestDTO messageRequestDTO, User user, ChatRoom chatRoom) {
        if (user == null) {
            throw new IllegalStateException("User must be authenticated to send messages");
        }

        chatRoomService.validateRoomIsNotArchived(chatRoom, "send messages");
        validateMessageForSave(messageRequestDTO.content(), MAX_LENGTH, messageRequestDTO.attachments());

        var message = Message.builder()
                .content(messageRequestDTO.content())
                .sender(user)
                .chatRoom(chatRoom)
                .replyTo(resolveReplyParent(messageRequestDTO.replyToMessageId(), chatRoom))
                .build();

        return messageRepository.save(message);
    }

    private Message resolveReplyParent(Long replyToMessageId, ChatRoom chatRoom) {
        if (replyToMessageId == null) {
            return null;
        }
        var parent = messageRepository.findById(replyToMessageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Replied-to message not found with ID: " + replyToMessageId));
        if (parent.getChatRoom() == null || !parent.getChatRoom().getId().equals(chatRoom.getId())) {
            throw new IllegalArgumentException("Cannot reply to a message from another chat room");
        }
        if (Boolean.TRUE.equals(parent.getDeleted()) || Boolean.TRUE.equals(parent.getQuarantined())) {
            throw new IllegalArgumentException("Cannot reply to a removed message");
        }
        return parent;
    }

    private MessagePageDTO getPreviousMessages(Long chatRoomId, Long messageId, int limit, boolean isStaff) {
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<MessageResponseDTO> messageResponseDTOS = messageRepository
                .findPreviousMessages(chatRoomId, messageId, isStaff, pageable)
                .stream()
                .map(message -> messageMapper.toMessageResponseDTO(message, isStaff))
                .collect(Collectors.toList())
                .reversed();

        boolean hasPrevious = messageResponseDTOS.size() > limit;

        if (hasPrevious) {
            messageResponseDTOS.removeFirst();
        }

        messageResponseDTOS = messagePromotionEnrichmentService.enrich(messageResponseDTOS);

        boolean hasNext = checkIfHasNext(chatRoomId, messageId, isStaff);

        return new MessagePageDTO(
                messageResponseDTOS,
                hasPrevious,
                hasNext,
                messageResponseDTOS.isEmpty() ? null : messageResponseDTOS.getFirst().id(),
                messageResponseDTOS.isEmpty() ? null : messageResponseDTOS.getLast().id());
    }

    private MessagePageDTO getNextMessages(Long chatRoomId, Long messageId, int limit, boolean isStaff) {
        Pageable pageable = PageRequest.of(0, limit + 1);
        List<MessageResponseDTO> messageResponseDTOS = messageRepository
                .findNextMessages(chatRoomId, messageId, isStaff, pageable)
                .stream()
                .map(message -> messageMapper.toMessageResponseDTO(message, isStaff))
                .collect(Collectors.toList());

        boolean hasNext = messageResponseDTOS.size() > limit;

        if (hasNext) {
            messageResponseDTOS.removeLast();
        }

        messageResponseDTOS = messagePromotionEnrichmentService.enrich(messageResponseDTOS);

        boolean hasPrevious = checkIfHasPrevious(chatRoomId, messageId, isStaff);

        return new MessagePageDTO(
                messageResponseDTOS,
                hasPrevious,
                hasNext,
                messageResponseDTOS.isEmpty() ? null : messageResponseDTOS.getFirst().id(),
                messageResponseDTOS.isEmpty() ? null : messageResponseDTOS.getLast().id());
    }

    private MessagePageDTO getMessagesAround(Long chatRoomId, Long aroundMessageId, int limit, boolean isStaff) {
        int halfLimit = limit / 2;

        Pageable beforePageable = PageRequest.of(0, halfLimit);
        Pageable afterPageable = PageRequest.of(0, limit - halfLimit);

        List<Message> before = messageRepository.findPreviousMessages(chatRoomId, aroundMessageId, isStaff,
                beforePageable);
        List<Message> after = messageRepository.findNextMessages(chatRoomId, aroundMessageId, isStaff, afterPageable);

        List<Message> allMessages = new ArrayList<>(before.reversed());

        messageRepository.findById(aroundMessageId, isStaff).ifPresent(message -> {
            if (message.getChatRoom().getId().equals(chatRoomId)) {
                allMessages.add(message);
            }
        });

        allMessages.addAll(after);

        List<MessageResponseDTO> messageResponseDTOS = messagePromotionEnrichmentService.enrich(
                allMessages.stream()
                        .map(message -> messageMapper.toMessageResponseDTO(message, isStaff))
                        .limit(limit)
                        .toList());

        if (messageResponseDTOS.isEmpty()) {
            return new MessagePageDTO(List.of(), false, false, null, null);
        }

        boolean hasPrevious = checkIfHasPrevious(chatRoomId, messageResponseDTOS.getFirst().id(), isStaff);
        boolean hasNext = checkIfHasNext(chatRoomId, messageResponseDTOS.getLast().id(), isStaff);

        return new MessagePageDTO(
                messageResponseDTOS,
                hasPrevious,
                hasNext,
                messageResponseDTOS.getFirst().id(),
                messageResponseDTOS.getLast().id());
    }

    private MessagePageDTO getLatestMessages(Long chatRoomId, int limit, boolean isStaff) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id"));
        List<MessageResponseDTO> messageResponseDTOS = messagePromotionEnrichmentService.enrich(
                messageRepository
                        .findVisibleByChatRoomId(chatRoomId, isStaff, pageable)
                        .stream()
                        .map(message -> messageMapper.toMessageResponseDTO(message, isStaff))
                        .toList()
                        .reversed());

        if (messageResponseDTOS.isEmpty()) {
            return new MessagePageDTO(List.of(), false, false, null, null);
        }

        boolean hasPrevious = checkIfHasPrevious(chatRoomId, messageResponseDTOS.getFirst().id(), isStaff);
        boolean hasNext = false;

        return new MessagePageDTO(
                messageResponseDTOS,
                hasPrevious,
                hasNext,
                messageResponseDTOS.getFirst().id(),
                messageResponseDTOS.getLast().id());
    }

    @Override
    public MessagePageDTO getPaginatedMessages(Long chatRoomId, Long afterMessageId, Long beforeMessageId,
                                               Long aroundMessageId, Role role) {
        int DEFAULT_MESSAGE_LIMIT = 50;

        if (beforeMessageId != null) {
            return getPreviousMessages(chatRoomId, beforeMessageId, DEFAULT_MESSAGE_LIMIT, role.isStaffMember());
        }

        if (afterMessageId != null) {
            return getNextMessages(chatRoomId, afterMessageId, DEFAULT_MESSAGE_LIMIT, role.isStaffMember());
        }

        if (aroundMessageId != null) {
            return getMessagesAround(chatRoomId, aroundMessageId, DEFAULT_MESSAGE_LIMIT, role.isStaffMember());
        }

        return getLatestMessages(chatRoomId, DEFAULT_MESSAGE_LIMIT, role.isStaffMember());
    }

    @Override
    public Long getMessageCount(Long roomId, Role role) {
        if (roomId == null) {
            throw new IllegalArgumentException("Chat room ID cannot be null");
        }
        return messageRepository.countVisibleByChatRoomId(roomId, role.isStaffMember());
    }

    @Override
    public Page<MessageResponseDTO> searchMessages(Long roomId, MessageSearchRequestDTO request, Role role) {
        Pageable pageable = PageRequest.of(request.page(), request.size(), Sort.by(Sort.Direction.DESC, "id"));

        Page<Message> messagesPage = messageRepository.findAll(
                MessageSpecification.getSpecification(
                        roomId,
                        request,
                        role.isStaffMember()),
                pageable);

        return messagePromotionEnrichmentService.enrich(
                messagesPage.map(message -> messageMapper.toMessageResponseDTO(message, role.isStaffMember())));
    }

    @Override
    public Message findById(Long messageId, Role role) {
        if (messageId == null) {
            throw new IllegalArgumentException("Message ID cannot be null");
        }
        return messageRepository.findById(messageId, role.isStaffMember())
                .orElseThrow(
                        () -> new IllegalArgumentException("Message not found or not visible with ID: " + messageId));
    }

    @Override
    public Message findById(Long messageId) {
        return messageRepository.findById(messageId).orElseThrow(
                () -> new IllegalArgumentException("Message not found with ID: " + messageId));
    }

    @Override
    public Message getLastMessage(Long chatRoomId, Role role) {
        if (chatRoomId == null) {
            throw new IllegalArgumentException("Chat room ID cannot be null");
        }
        return messageRepository.findTopVisibleByChatRoomIdOrderByIdDesc(chatRoomId, role.isStaffMember());
    }

    @Override
    public Integer countByChatRoomIdAndIdGreaterThan(Long chatRoomId, Long id, Role role) {
        if (chatRoomId == null || id == null) {
            throw new IllegalArgumentException("Chat room ID and message ID cannot be null");
        }
        return messageRepository.countVisibleByChatRoomIdAndIdGreaterThan(chatRoomId, id, role.isStaffMember());
    }

    @Transactional
    @Override
    public void deleteAllUserMessages(User user) {
        var messages = messageRepository.findAllBySender(user);
        messages.forEach(message -> {
            message.setDeleted(true);
            roomActivityService.removeMessageReactions(message.getChatRoom().getId(), message.getId());
        });
        messageRepository.saveAll(messages);
    }

    @Override
    public void deleteUserMessagesAfter(User user, Instant createdAt) {
        if (user == null || createdAt == null) {
            throw new IllegalArgumentException("User and duration cannot be null");
        }
        var messages = messageRepository.findAllBySenderAndCreatedAtAfter(user, createdAt);
        deleteMessages(messages);
    }

    @Override
    public Message findNearestPreviousNotDeletedMessage(Long chatRoomId, Long currentMessageId) {
        List<Message> messages = messageRepository.findPreviousMessages(
                chatRoomId,
                currentMessageId,
                false,
                PageRequest.of(0, 1));
        return messages.isEmpty() ? null : messages.getFirst();
    }

    @Override
    public void deleteMessage(Long messageId) {
        var message = messageRepository.findById(messageId, false)
                .orElseThrow(
                        () -> new IllegalArgumentException("Message not found or not visible with ID: " + messageId));
        deleteMessage(message);
    }

    public Message deleteMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (message.getDeleted()) {
            return message;
        }
        var chatRoom = message.getChatRoom();
        var chatRoomId = chatRoom.getId();
        if (chatRoom.getType() != ChatRoomType.PRIVATE) {
            roomActivityService.setMessageCount(
                    chatRoomId.toString(),
                    getMessageCount(chatRoomId, Role.GUEST) - 1);
            roomActivityService.removeMessageReactions(chatRoomId, message.getId());
        }
        message.setDeleted(true);
        return messageRepository.save(message);
    }

    @Override
    public void broadcastMessageDeletion(Message message) {
        var chatRoom = message.getChatRoom();
        var messageDTO = messageMapper.toMessageResponseDTO(message);

        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            var webSocketMessage = WebSocketMessage.builder()
                    .type(WebSocketMessageType.PRIVATE_MESSAGE_DELETE)
                    .chatRoomName(null)
                    .data(messageDTO)
                    .build();
            sendToPrivateMembers(chatRoom.getId(), webSocketMessage);
            return;
        }

        var webSocketMessage = WebSocketMessage.builder()
                .type(WebSocketMessageType.DELETE_MESSAGE)
                .chatRoomName(chatRoom.getName())
                .data(messageDTO)
                .build();

        webSocketBroadcastService.broadcastToChatRoom(chatRoom.getName(), webSocketMessage);
    }

    @Transactional
    @Override
    public Message editMessage(Long messageId, String content, User user) {
        var message = findById(messageId);
        if (!Objects.equals(user.getId(), message.getSender().getId())) {
            throw new IllegalArgumentException(
                    "Messages of other users cannot be edited" + messageId);
        }

        validateMessageForEdit(content, MAX_LENGTH, message.getAttachments().size());

        messageEditHistoryService.save(message.getContent(), message, new ArrayList<>(message.getAttachments()), user);
        message.setContent(content);
        message.setEditedAt(Instant.now());

        var updatedMessage = messageRepository.save(message);

        broadcastMessageEdit(messageMapper.toMessageResponseDTO(updatedMessage));

        return updatedMessage;
    }

    @Transactional
    @Override
    public Message removeAttachmentFromMessage(Long messageId, Long attachmentId, User user) {
        var message = findById(messageId);
        if (!Objects.equals(user.getId(), message.getSender().getId())) {
            throw new IllegalArgumentException(
                    "Messages of other users cannot be removed" + messageId);
        }

        var attachmentIds = message.getAttachments().stream().map(Attachment::getId).toList();

        attachmentService.softDeleteAttachments(List.of(attachmentId));

        if (messageIsEmpty(message.getContent(), message.getAttachments().size() - 1)) {
            throw new IllegalArgumentException(
                    "Message cannot be left empty after removing attachment");
        }

        messageEditHistoryService.save(message.getContent(), message, new ArrayList<>(message.getAttachments()), user);
        message.setEditedAt(Instant.now());

        var updatedMessage = messageRepository.save(message);

        var filteredMessageDTO = filterAttachments(
                messageMapper.toMessageResponseDTO(updatedMessage),
                List.of(attachmentId));
        broadcastMessageEdit(filteredMessageDTO);

        return updatedMessage;
    }

    @Override
    public List<MessageEditHistoryDTO> getMessageHistory(Long messageId) {
        var message = findById(messageId);
        return messageEditHistoryMapper.toDtos(messageEditHistoryService.findByMessage(message));
    }

    private boolean checkIfHasNext(Long chatRoomId, Long messageId, boolean isStaff) {
        if (chatRoomId == null || messageId == null) {
            return false;
        }
        return messageRepository.existsByChatRoomIdAndIdGreaterThan(chatRoomId, messageId, isStaff);
    }

    private boolean checkIfHasPrevious(Long chatRoomId, Long messageId, boolean isStaff) {
        if (chatRoomId == null || messageId == null) {
            return false;
        }
        return messageRepository.existsByChatRoomIdAndIdLessThan(chatRoomId, messageId, isStaff);
    }

    private void deleteMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Long chatRoomId = messages.getFirst().getChatRoom().getId();

        long newCount = Math.max(0, getMessageCount(chatRoomId, Role.GUEST) - messages.size());
        roomActivityService.setMessageCount(chatRoomId.toString(), newCount);

        messages.forEach(message -> {
            message.setDeleted(true);
            roomActivityService.removeMessageReactions(chatRoomId, message.getId());
        });
        messageRepository.saveAll(messages);
    }

    private boolean messageIsEmpty(String content, int attachmentCount) {
        return (content == null || content.isEmpty()) && attachmentCount == 0;
    }

    @Override
    public MessageResponseDTO broadcastMessageEdit(MessageResponseDTO messageResponseDTO) {
        messageResponseDTO = messagePromotionEnrichmentService.enrich(messageResponseDTO);
        ChatRoom chatRoom = chatRoomService.findById(messageResponseDTO.chatRoomId());

        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            var webSocketMessage = WebSocketMessage.builder()
                    .type(WebSocketMessageType.PRIVATE_MESSAGE_EDIT)
                    .chatRoomName(null)
                    .data(messageResponseDTO)
                    .build();
            sendToPrivateMembers(chatRoom.getId(), webSocketMessage);
            return messageResponseDTO;
        }

        var webSocketMessage = WebSocketMessage.builder()
                .type(WebSocketMessageType.MESSAGE_EDIT)
                .chatRoomName(messageResponseDTO.chatRoomName())
                .data(messageResponseDTO)
                .build();

        webSocketBroadcastService.broadcastToChatRoom(messageResponseDTO.chatRoomName(), webSocketMessage);

        return messageResponseDTO;
    }
}
