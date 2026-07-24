package com.mk3.chatapp.specifications;

import com.mk3.chatapp.dtos.requests.MessageSearchRequestDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.Message;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class MessageSpecification {

    public static Specification<Message> getSpecification(Long chatRoomId, MessageSearchRequestDTO request,
                                                          boolean isStaff) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (chatRoomId != null) {
                predicates.add(criteriaBuilder.equal(root.get("chatRoom").get("id"), chatRoomId));
            } else {
                // Cross-room user-messages list must not leak private-chat messages;
                // private conversations are reviewed separately via AdminConversationService.
                predicates.add(criteriaBuilder.notEqual(
                        root.get("chatRoom").get("type"), ChatRoomType.PRIVATE));
            }

            if (request.senderUsername() != null && !request.senderUsername().isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("sender").get("username")),
                        "%" + request.senderUsername().toLowerCase() + "%"));
            }

            if (request.content() != null && !request.content().isBlank()) {
                // Match against the marker-stripped copy so **bold**/*italic*
                // markers can't break phrase matches; pre-column rows have a
                // null contentPlain and fall back to raw content.
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(
                                root.<String>get("contentPlain"), root.get("content"))),
                        "%" + request.content().toLowerCase() + "%"));
            }

            if (request.attachmentName() != null && !request.attachmentName().isBlank()) {
                Join<Message, Attachment> attachments = root.join("attachments", JoinType.LEFT);
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(attachments.get("name")),
                        "%" + request.attachmentName().toLowerCase() + "%"));
                if (!isStaff) {
                    predicates.add(criteriaBuilder.equal(attachments.get("deleted"), false));
                }
            }

            if (!isStaff) {
                predicates.add(criteriaBuilder.equal(root.get("deleted"), false));
            }

            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
