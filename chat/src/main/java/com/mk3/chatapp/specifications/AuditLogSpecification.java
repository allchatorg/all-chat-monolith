package com.mk3.chatapp.specifications;

import com.mk3.chatapp.dtos.requests.AuditLogSearchRequestDTO;
import com.mk3.chatapp.enums.AuditLogActorType;
import com.mk3.chatapp.models.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AuditLogSpecification {
    private static final String SYSTEM_AUDITOR = "system";

    private static Specification<AuditLog> getSearchSpecification(AuditLogSearchRequestDTO filterDto) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filterDto.userId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetUserId"), filterDto.userId()));
            }

            if (filterDto.targetUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetUserId"), filterDto.targetUserId()));
            }

            if (filterDto.createdByUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), String.valueOf(filterDto.createdByUserId())));
            }

            if (filterDto.createdByType() != null) {
                if (filterDto.createdByType() == AuditLogActorType.SYSTEM) {
                    predicates.add(criteriaBuilder.equal(root.get("createdBy"), SYSTEM_AUDITOR));
                } else {
                    predicates.add(criteriaBuilder.isNotNull(root.get("createdBy")));
                    predicates.add(criteriaBuilder.notEqual(root.get("createdBy"), SYSTEM_AUDITOR));
                }
            }

            if (filterDto.auditLogType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("logType"), filterDto.auditLogType()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<AuditLog> getCompleteSpecification(AuditLogSearchRequestDTO filterDto) {
        return getSearchSpecification(filterDto);
    }
}
