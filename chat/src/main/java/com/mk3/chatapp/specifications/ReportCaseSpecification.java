package com.mk3.chatapp.specifications;

import com.mk3.chatapp.dtos.requests.ReportSearchRequestDTO;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.utils.Utils;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReportCaseSpecification {

    private static Specification<ReportCase> getSearchSpecification(ReportSearchRequestDTO filterDto) {
        return ((root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filterDto.reportedUserUsernameOrId() != null) {
                try {
                    Long id = Long.parseLong(filterDto.reportedUserUsernameOrId());
                    predicates.add(criteriaBuilder.equal(root.get("message").get("sender").get("id"), id));
                } catch (NumberFormatException e) {
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("message").get("sender").get("username")),
                            "%" + filterDto.reportedUserUsernameOrId().toLowerCase() + "%"
                    ));
                }
            }

            if (filterDto.resolved() != null && filterDto.resolved()) {
                predicates.add(criteriaBuilder.isNotNull(root.get("resolutionDate")));
            } else if (filterDto.resolved() != null) {
                predicates.add(criteriaBuilder.isNull(root.get("resolutionDate")));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        });
    }

    private static Specification<ReportCase> getSortSpecification(ReportSearchRequestDTO filterDto) {
        return (root, query, criteriaBuilder) -> {
            Optional<Sort.Order> reportCountSortOrder = getReportCountSortOrder(filterDto.sort());
            if (reportCountSortOrder.isPresent() && query.getResultType() != Long.class) {
                var join = root.join("reports", JoinType.LEFT);
                query.groupBy(root.get("id")); // Group by primary key instead of entire root
                query.orderBy(reportCountSortOrder.get().getDirection().isAscending()
                        ? criteriaBuilder.asc(criteriaBuilder.count(join))
                        : criteriaBuilder.desc(criteriaBuilder.count(join)));
            }
            return criteriaBuilder.conjunction();
        };
    }

    public static Specification<ReportCase> getCompleteSpecification(ReportSearchRequestDTO filterDto) {
        Specification<ReportCase> searchSpec = getSearchSpecification(filterDto);
        Specification<ReportCase> sortSpec = getSortSpecification(filterDto);
        return searchSpec.and(sortSpec);
    }

    private static Optional<Sort.Order> getReportCountSortOrder(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            Utils.jsonStringToSortOrder(jsonString).forEach(System.out::println);
            return Utils.jsonStringToSortOrder(jsonString)
                    .stream()
                    .filter(r -> "reportCount".equals(r.getProperty()))
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}