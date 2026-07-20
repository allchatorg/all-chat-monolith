package com.example.adsportalbe.specifications;

import com.example.adsportalbe.dto.promotion.PromotedMessageSearchRequestDto;
import com.example.adsportalbe.models.promotion.PromotedMessage;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PromotedMessageSpecification {
    public static Specification<PromotedMessage> getSpecification(PromotedMessageSearchRequestDto filterDto) {
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

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
