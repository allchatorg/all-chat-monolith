package com.mk3.chatapp.specifications;

import com.mk3.chatapp.dtos.requests.UserSearchRequestDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {
    public static Specification<User> getSpecification(UserSearchRequestDTO filterDto) {
        return ((root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filterDto.usernameOrId() != null) {
                try {
                    Long id = Long.parseLong(filterDto.usernameOrId());
                    predicates.add(criteriaBuilder.equal(root.get("id"), id));
                } catch (NumberFormatException e) {
                    predicates.add(criteriaBuilder.like(root.get("username"), "%" + filterDto.usernameOrId() + "%"));
                }
            }

            if (filterDto.roles() != null && !filterDto.roles().isEmpty()) {
                predicates.add(root.get("role").in(filterDto.roles()));
            }

            if (filterDto.over18() != null) {
                predicates.add(criteriaBuilder.equal(root.get("over18"), filterDto.over18()));
            }

            if (filterDto.claimed() != null) {
                predicates.add(criteriaBuilder.equal(root.get("claimed"), filterDto.claimed()));
            }

            if (filterDto.verified() != null) {
                predicates.add(criteriaBuilder.equal(root.get("verified"), filterDto.verified()));
            }

            if (filterDto.banned() != null) {
                predicates.add(criteriaBuilder.equal(root.get("banned"), filterDto.banned()));
            }

            predicates.add(criteriaBuilder.notEqual(root.get("role"), Role.GUEST));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        });
    }
}
