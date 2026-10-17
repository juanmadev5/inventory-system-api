package com.jm.inventory.infrastructure.warehouse.persistence;

import com.jm.inventory.domain.warehouse.WarehouseFilter;
import org.springframework.data.jpa.domain.Specification;

public final class WarehouseSpecifications {

    private WarehouseSpecifications() {
    }

    public static Specification<WarehouseJpaEntity> matching(WarehouseFilter filter) {
        return Specification.allOf((root, query, builder) -> filter.active() == null
                ? null
                : builder.equal(root.get("active"), filter.active()));
    }
}
