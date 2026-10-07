package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.ReactionDetailsDTO;
import com.mk3.chatapp.dtos.responses.ReactionSocketResponse;
import com.mk3.chatapp.dtos.responses.UserMinimalDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.ReactionType;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.mappers.ReactionMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Reaction;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.ReactionRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.ChatRoomService;
import com.mk3.chatapp.services.PrivateChatService;
import com.mk3.chatapp.services.ProReactionService;
import com.mk3.chatapp.services.ReactionService;
import com.mk3.chatapp.services.RoomActivityService;
import com.mk3.chatapp.services.SecurityService;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class ReactionServiceImpl implements ReactionService {
    private final ReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final ProReactionService proReactionService;
    private final ChatRoomService chatRoomService;
    private final PrivateChatService privateChatService;
    private final SecurityService securityService;
    private final RoomActivityService roomActivityService;

    private final WebSocketBroadcastService webSocketBroadcastService;
    private final ReactionMapper reactionMapper;
    private final UserChatRoomRepository userChatRoomRepository;

    @Transactional
    @Override
    public void addReaction(User user, Long messageId, String emoji, String emojiId) {
        Message message = lockMessageForReaction(messageId, user);
        assertCanAddPrivateReaction(message.getChatRoom(), user);
        proReactionService.validateForAdd(emoji, emojiId, user.getId());
        Optional<Reaction> reactionOpt = reactionRepository.findByMessageIdAndEmoji(messageId, emoji);
        Reaction reaction;

        if (reactionOpt.isPresent()) {
            reaction = reactionOpt.get();
            // Check if user already reacted using the locked entity (no extra query needed)
            boolean alreadyReacted = reaction.getUsers().stream()
                    .anyMatch(u -> u.getId().equals(user.getId()));
            if (alreadyReacted) {
                return;
            }
        } else {
            reaction = Reaction.builder()
                    .emoji(emoji)
                    .emojiId(emojiId)
                    .message(message)
                    .build();
        }
        reaction.getUsers().add(user);
        reactionRepository.save(reaction);

        publishAfterCommit(reaction, ReactionType.ADD, user);
    }

    @Transactional
    @Override
    public void removeReaction(User user, Long messageId, String emoji, String emojiId) {
        lockMessageForReaction(messageId, user);
        proReactionService.validateIdentity(emoji, emojiId);
        Optional<Reaction> reactionOpt = findByMessageIdAndEmoji(messageId, emoji);

        if (reactionOpt.isEmpty()) {
            return;
        }

        Reaction reaction = reactionOpt.get();
        boolean removed = reaction.getUsers().removeIf(member -> member.getId().equals(user.getId()));
        if (!removed) {
            return;
        }
        if (reaction.getUsers().isEmpty()) {
            reactionRepository.delete(reaction);
        } else {
            reactionRepository.save(reaction);
        }
        publishAfterCommit(reaction, ReactionType.REMOVE, user);
    }

    @Override
    public Reaction findById(Long id) {
        return reactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reaction not found with id: " + id));
    }

    @Override
    public Optional<Reaction> findByMessageIdAndEmoji(Long messageId, String emoji) {
        return reactionRepository.findByMessageIdAndEmoji(messageId, emoji);
    }

    @Override
    public Reaction save(Reaction reaction) {
        return reactionRepository.save(reaction);
    }

    @Override
    public boolean userHasReacted(Long reactionId, Long userId) {
        Reaction reaction = findById(reactionId);
        return reaction.getUsers().stream()
                .anyMatch(user -> user.getId().equals(userId));
    }

    @Override
    public boolean userHasReacted(String emoji, Long messageId, Long userId) {
        Optional<Reaction> reactionOpt = findByMessageIdAndEmoji(messageId, emoji);
        return reactionOpt.map(reaction -> reaction.getUsers().stream()
                .anyMatch(user -> user.getId().equals(userId))).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public ReactionDetailsDTO findByMessageIdAndEmoji(Long messageId, String emoji, Integer limit) {
        if (limit != null && limit < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reaction user limit cannot be negative");
        }
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        assertMessageAccess(message, securityService.getCurrentUser());
        proReactionService.validateIdentity(emoji, emoji);
        return findByMessageIdAndEmoji(messageId, emoji)
                .map(reaction -> {
                    // Limit only the response, never the managed collection: changing it
                    // during a GET can delete reaction_user rows when JPA flushes.
                    ReactionDetailsDTO details = reactionMapper.toDetailsDTO(reaction);
                    Set<UserMinimalDTO> limitedUsers = details.users().stream()
                            .limit(limit != null ? limit : Integer.MAX_VALUE)
                            .collect(Collectors.toSet());
                    return new ReactionDetailsDTO(details.id(), details.messageId(), details.emoji(),
                            details.emojiId(), details.usersCount(), limitedUsers);
                }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reaction not found"));
    }

    private Message lockMessageForReaction(Long messageId, User user) {
        // Serialize both add and remove, including when no reaction row exists yet.
        // The lock remains held until the membership transaction commits.
        Message message = messageRepository.findByIdForReactionUpdate(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        assertMessageAccess(message, user);
        chatRoomService.validateRoomIsNotArchived(message.getChatRoom(), "react to messages");
        return message;
    }

    private void assertMessageAccess(Message message, User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to access reactions");
        }
        if (Boolean.TRUE.equals(message.getDeleted()) || Boolean.TRUE.equals(message.getQuarantined())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        ChatRoom room = message.getChatRoom();
        try {
            if (room.getType() == ChatRoomType.PRIVATE) {
                privateChatService.assertMember(user, room);
            } else {
                chatRoomService.validateUserCanJoinChatRoom(user, room);
            }
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    private void assertCanAddPrivateReaction(ChatRoom room, User user) {
        if (room.getType() != ChatRoomType.PRIVATE) return;
        if (!user.getRole().isStaffMember()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Private messaging is available to staff only");
        }
        User counterpart = privateChatService.getCounterpart(user, room);
        if (counterpart != null && (hasBlocked(user, counterpart) || hasBlocked(counterpart, user))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot add a reaction in a blocked conversation");
        }
    }

    private boolean hasBlocked(User blocker, User other) {
        return blocker.getBlockedUsers() != null && blocker.getBlockedUsers().stream()
                .anyMatch(blocked -> blocked.getId().equals(other.getId()));
    }

    private ReactionSocketResponse createReactionSocketResponse(Reaction reaction, ReactionType responseType,
                                                                User user) {
        return new ReactionSocketResponse(
                reaction.getId(),
                reaction.getMessage().getChatRoom().getId(),
                reaction.getMessage().getId(),
                responseType,
                reaction.getEmoji(),
                reaction.getEmojiId(),
                new UserMinimalDTO(user.getId(), user.getApplicationUsername(), user.isProBadgeVisible(), user.getProBadgeRevision(),
                        user.getEffectiveUsernameFont(), user.getEffectiveMessageFont(), user.getFontRevision()));
    }

    private void publishAfterCommit(Reaction reaction, ReactionType responseType, User user) {
        ChatRoom room = reaction.getMessage().getChatRoom();
        var response = createReactionSocketResponse(reaction, responseType, user);
        // Resolve lazy member collections inside the transaction. Delivery must not
        // expose uncommitted membership or roll it back after a Redis/socket failure.
        boolean privateRoom = room.getType() == ChatRoomType.PRIVATE;
        List<Long> recipients = privateRoom ? userChatRoomRepository.findByChatRoom(room).stream()
                .map(member -> member.getUser().getId()).toList() : List.of();
        String roomName = room.getName();
        Long roomId = room.getId();
        WebSocketMessage payload = new WebSocketMessage(WebSocketMessageType.MESSAGE_REACTION_UPDATE,
                privateRoom ? null : roomName, response);
        Runnable publish = () -> {
            try {
                if (responseType == ReactionType.ADD) {
                    roomActivityService.incrementReactionCount(response.chatroomId(), response.messageId());
                } else {
                    roomActivityService.decrementReactionCount(response.chatroomId(), response.messageId());
                }
            } catch (RuntimeException exception) {
                log.error("Failed to update reaction activity for message {}", response.messageId(), exception);
            }
            if (privateRoom) {
                for (Long recipient : recipients) {
                    try {
                        webSocketBroadcastService.sendPrivateToUser(recipient, payload);
                    } catch (RuntimeException exception) {
                        log.error("Failed to deliver reaction for message {} to user {}",
                                response.messageId(), recipient, exception);
                    }
                }
            } else {
                try {
                    webSocketBroadcastService.broadcastToChatRoom(roomId, payload);
                } catch (RuntimeException exception) {
                    log.error("Failed to broadcast reaction for message {}", response.messageId(), exception);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
