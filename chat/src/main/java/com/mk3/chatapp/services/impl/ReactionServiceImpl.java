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
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ReactionRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.ReactionService;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ReactionServiceImpl implements ReactionService {
    private final ReactionRepository reactionRepository;

    private final WebSocketBroadcastService webSocketBroadcastService;
    private final ReactionMapper reactionMapper;
    private final UserChatRoomRepository userChatRoomRepository;

    @Transactional
    @Override
    public void addReaction(User user, Message message, String emoji, String emojiId) {
        // Use pessimistic lock to prevent race condition where two concurrent requests
        // both see no existing reaction and create duplicate rows.
        Optional<Reaction> reactionOpt = reactionRepository.findByMessageIdAndEmojiForUpdate(message.getId(), emoji);
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

        broadcastReaction(reaction, ReactionType.ADD, user.getId(), user.getUsername());
    }

    @Transactional
    @Override
    public void removeReaction(User user, Message message, String emoji, String emojiId) {
        Optional<Reaction> reactionOpt = findByMessageIdAndEmoji(message.getId(), emoji);

        if (reactionOpt.isEmpty()) {
            return;
        }

        Reaction reaction = reactionOpt.get();
        if (!userHasReacted(emoji, message.getId(), user.getId())) {
            return;
        }

        reaction.getUsers().remove(user);
        if (reaction.getUsers().isEmpty()) {
            reactionRepository.delete(reaction);
        } else {
            reactionRepository.save(reaction);
        }
        broadcastReaction(reaction, ReactionType.REMOVE, user.getId(), user.getUsername());
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
    public ReactionDetailsDTO findByMessageIdAndEmoji(Long messageId, String emoji, Integer limit) {
        return findByMessageIdAndEmoji(messageId, emoji)
                .map(reaction -> {
                    Set<User> limitedUsers = reaction.getUsers().stream()
                            .limit(limit != null ? limit : Integer.MAX_VALUE)
                            .collect(Collectors.toSet());

                    reaction.setUsers(limitedUsers);

                    return reactionMapper.toDetailsDTO(reaction);
                }).orElseThrow(
                        () -> new RuntimeException(
                                "Reaction not found with messageId: " + messageId + " and emoji: " + emoji));
    }

    private ReactionSocketResponse createReactionSocketResponse(Reaction reaction, ReactionType responseType,
                                                                Long userId, String username) {
        return new ReactionSocketResponse(
                reaction.getId(),
                reaction.getMessage().getChatRoom().getId(),
                reaction.getMessage().getId(),
                responseType,
                reaction.getEmoji(),
                reaction.getEmojiId(),
                new UserMinimalDTO(userId, username));
    }

    private void broadcastReaction(Reaction reaction, ReactionType responseType, Long userId, String username) {
        var chatRoom = reaction.getMessage().getChatRoom();
        var socketResponse = createReactionSocketResponse(reaction, responseType, userId, username);

        // Private rooms have a null name, so they can't be addressed via the
        // /topic/chat-room.{name} destination. Deliver the reaction to each
        // member's private queue instead, mirroring MessagesServiceImpl.
        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            sendReactionToPrivateMembers(chatRoom,
                    new WebSocketMessage(
                            WebSocketMessageType.MESSAGE_REACTION_UPDATE,
                            null,
                            socketResponse));
            return;
        }

        webSocketBroadcastService.broadcastToChatRoom(
                chatRoom.getName(),
                new WebSocketMessage(
                        WebSocketMessageType.MESSAGE_REACTION_UPDATE,
                        chatRoom.getName(),
                        socketResponse));
    }

    private void sendReactionToPrivateMembers(ChatRoom chatRoom, WebSocketMessage payload) {
        List<UserChatRoom> members = userChatRoomRepository.findByChatRoom(chatRoom);
        for (UserChatRoom member : members) {
            webSocketBroadcastService.sendPrivateToUser(member.getUser().getId(), payload);
        }
    }
}