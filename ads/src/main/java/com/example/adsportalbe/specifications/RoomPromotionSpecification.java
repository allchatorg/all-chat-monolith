package com.example.adsportalbe.specifications;

import com.example.adsportalbe.dto.roompromotion.RoomPromotionSearchRequestDto;
import com.example.adsportalbe.models.promotion.RoomPromotion;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class RoomPromotionSpecification {
    public static Specification<RoomPromotion> getSpecification(RoomPromotionSearchRequestDto filterDto) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filterDto.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filterDto.status()));
            }

            if (filterDto.userId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("owner").get("id"), filterDto.userId()));
            }

            if (filterDto.email() != null && !filterDto.email().isBlank()) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("owner").get("email")),
                                "%" + filterDto.email().toLowerCase() + "%"));
            }

            if (filterDto.chatRoomId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("chatRoom").get("id"), filterDto.chatRoomId()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
