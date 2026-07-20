package com.example.adsportalbe.services;

import com.mk3.chatapp.services.MessagePromotionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * In-process implementation of the chat module's {@link MessagePromotionPort},
 * mirroring {@link AdsModerationAdapter}. Keeps the dependency direction
 * {@code ads -> chat}.
 */
@Service
@RequiredArgsConstructor
public class MessagePromotionAdapter implements MessagePromotionPort {

    private final PromotedMessageService promotedMessageService;

    @Override
    public void cancelActivePromotionForMessage(Long messageId, boolean removedByStaff) throws Exception {
        promotedMessageService.cancelForMessageRemoval(messageId, removedByStaff);
    }

    @Override
    public Map<Long, PromotionInfo> getActivePromotions(Collection<Long> messageIds) {
        return promotedMessageService.getActivePromotions(messageIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new PromotionInfo(entry.getValue().id(), entry.getValue().status().name())));
    }

    @Override
    public Page<Long> getApprovedPromotedMessageIds(Long roomId, int page, int size) {
        return promotedMessageService.getApprovedPromotedMessageIds(roomId, page, size);
    }

    @Override
    public PromotionBanOutcome cancelPromotionsForBannedUser(Long userId) {
        PromotedMessageService.PromotionCancelOutcome outcome =
                promotedMessageService.cancelPromotionsForBannedUser(userId);
        return new PromotionBanOutcome(
                outcome.attempted(),
                outcome.released(),
                outcome.refunded(),
                outcome.totalReturned(),
                outcome.currency());
    }

    @Override
    public PromotionBanOutcome cancelPromotionsForDeletedUserMessages(Long userId, java.time.Instant cutoff) {
        PromotedMessageService.PromotionCancelOutcome outcome =
                promotedMessageService.cancelPromotionsForDeletedUserMessages(userId, cutoff);
        return new PromotionBanOutcome(
                outcome.attempted(),
                outcome.released(),
                outcome.refunded(),
                outcome.totalReturned(),
                outcome.currency());
    }
}
