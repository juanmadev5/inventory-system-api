package com.jm.inventory.infrastructure.alerting.persistence;

import com.jm.inventory.domain.alerting.ReorderThresholdFilter;
import org.springframework.data.jpa.domain.Specification;

public final class ReorderThresholdSpecifications {

    private ReorderThresholdSpecifications() {
    }

    public static Specification<ReorderThresholdJpaEntity> matching(ReorderThresholdFilter filter) {
        return Specification.<ReorderThresholdJpaEntity>allOf(
                (root, query, builder) -> filter.productId() == null
                        ? null
                        : builder.equal(root.get("productId"), filter.productId()),
                (root, query, builder) -> filter.warehouseId() == null
                        ? null
                        : builder.equal(root.get("warehouseId"), filter.warehouseId()));
    }
}
