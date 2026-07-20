package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.dtos.responses.PromotionInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Attaches active-promotion info to message DTOs with one batched port query
 * per page — never per message.
 */
@Service
@RequiredArgsConstructor
public class MessagePromotionEnrichmentService {

    private final MessagePromotionPort messagePromotionPort;

    public List<MessageResponseDTO> enrich(List<MessageResponseDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        List<Long> messageIds = messages.stream()
                .map(MessageResponseDTO::id)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, MessagePromotionPort.PromotionInfo> promotions =
                messagePromotionPort.getActivePromotions(messageIds);
        if (promotions.isEmpty()) {
            return messages;
        }

        return messages.stream()
                .map(message -> {
                    var info = promotions.get(message.id());
                    return info == null ? message
                            : message.withPromotion(new PromotionInfoDTO(info.id(), info.status()));
                })
                .toList();
    }

    public MessageResponseDTO enrich(MessageResponseDTO message) {
        if (message == null) {
            return null;
        }
        return enrich(List.of(message)).getFirst();
    }

    public Page<MessageResponseDTO> enrich(Page<MessageResponseDTO> page) {
        if (page == null || page.isEmpty()) {
            return page;
        }
        return new PageImpl<>(enrich(page.getContent()), page.getPageable(), page.getTotalElements());
    }
}
