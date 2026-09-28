package com.example.adsportalbe.specifications;

import com.example.adsportalbe.dto.requests.UserSearchRequestDto;
import com.example.adsportalbe.models.identity.AdminUserSummary;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {
    public static Specification<AdminUserSummary> getSpecification(UserSearchRequestDto filterDto) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filterDto.userId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), filterDto.userId()));
            }

            if (filterDto.email() != null && !filterDto.email().isBlank()) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("email")),
                                "%" + filterDto.email().toLowerCase() + "%"));
            }

            // Include purchasers of any product, even when their payments were refunded or are pending.
            predicates.add(criteriaBuilder.isTrue(root.get("hasPurchases")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
