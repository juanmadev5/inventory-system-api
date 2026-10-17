package com.jm.inventory.infrastructure.audit.persistence;

import com.jm.inventory.domain.audit.AuditLogFilter;
import org.springframework.data.jpa.domain.Specification;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLogJpaEntity> matching(AuditLogFilter filter) {
        return Specification.<AuditLogJpaEntity>allOf(
                (root, query, builder) -> filter.entityType() == null
                        ? null
                        : builder.equal(root.get("entityType"), filter.entityType()),
                (root, query, builder) -> filter.entityId() == null
                        ? null
                        : builder.equal(root.get("entityId"), filter.entityId()),
                (root, query, builder) -> filter.action() == null
                        ? null
                        : builder.equal(root.get("action"), filter.action()),
                (root, query, builder) -> filter.occurredFrom() == null
                        ? null
                        : builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.occurredFrom()),
                (root, query, builder) -> filter.occurredTo() == null
                        ? null
                        : builder.lessThanOrEqualTo(root.get("occurredAt"), filter.occurredTo()));
    }
}
