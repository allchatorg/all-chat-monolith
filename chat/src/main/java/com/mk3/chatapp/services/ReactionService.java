package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.ReactionDetailsDTO;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Reaction;
import com.mk3.chatapp.models.identity.User;

import java.util.Optional;

public interface ReactionService {

    void addReaction(User user, Message message, String emoji, String emojiId);

    void removeReaction(User user, Message message, String emoji, String emojiId);

    Reaction findById(Long id);

    Optional<Reaction> findByMessageIdAndEmoji(Long messageId, String emoji);

    Reaction save(Reaction reaction);

    boolean userHasReacted(Long reactionId, Long userId);

    boolean userHasReacted(String emoji, Long messageId, Long userId);

    ReactionDetailsDTO findByMessageIdAndEmoji(Long messageId, String emoji, Integer limit);
}
