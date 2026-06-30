package com.example.adsportalbe.specifications;

import com.example.adsportalbe.dto.requests.AdSearchRequestDto;
import com.example.adsportalbe.models.ad.Ad;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AdSpecification {
    public static Specification<Ad> getSpecification(AdSearchRequestDto filterDto) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filterDto.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filterDto.status()));
            }

            if (filterDto.types() != null && !filterDto.types().isEmpty()) {
                predicates.add(root.get("format").get("type").in(filterDto.types()));
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

            if (filterDto.approvedAtStart() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("approvedAt"),
                        filterDto.approvedAtStart()));
            }

            if (filterDto.approvedAtEnd() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("approvedAt"),
                        filterDto.approvedAtEnd()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
