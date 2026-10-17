package com.jm.inventory.infrastructure.cyclecount.persistence;

import com.jm.inventory.domain.cyclecount.CycleCountFilter;
import org.springframework.data.jpa.domain.Specification;

public final class CycleCountSpecifications {

    private CycleCountSpecifications() {
    }

    public static Specification<CycleCountJpaEntity> matching(CycleCountFilter filter) {
        return Specification.<CycleCountJpaEntity>allOf(
                (root, query, builder) -> filter.warehouseId() == null
                        ? null
                        : builder.equal(root.get("warehouseId"), filter.warehouseId()),
                (root, query, builder) -> filter.status() == null
                        ? null
                        : builder.equal(root.get("status"), filter.status()));
    }
}
