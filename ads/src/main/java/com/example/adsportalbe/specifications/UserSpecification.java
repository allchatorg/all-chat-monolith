package com.example.adsportalbe.specifications;

import com.example.adsportalbe.dto.requests.UserSearchRequestDto;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {
    public static Specification<User> getSpecification(UserSearchRequestDto filterDto) {
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

            // The ads-portal user list only shows advertisers, not the whole user base.
            predicates.add(criteriaBuilder.greaterThan(root.get("purchasedAdsCount"), 0L));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
