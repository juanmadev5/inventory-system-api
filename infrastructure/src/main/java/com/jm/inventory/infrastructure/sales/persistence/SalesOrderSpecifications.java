package com.jm.inventory.infrastructure.sales.persistence;

import com.jm.inventory.domain.sales.SalesOrderFilter;
import org.springframework.data.jpa.domain.Specification;

public final class SalesOrderSpecifications {

    private SalesOrderSpecifications() {
    }

    public static Specification<SalesOrderJpaEntity> matching(SalesOrderFilter filter) {
        return Specification.<SalesOrderJpaEntity>allOf(
                (root, query, builder) -> filter.customerId() == null
                        ? null
                        : builder.equal(root.get("customerId"), filter.customerId()),
                (root, query, builder) -> filter.warehouseId() == null
                        ? null
                        : builder.equal(root.get("warehouseId"), filter.warehouseId()),
                (root, query, builder) -> filter.status() == null
                        ? null
                        : builder.equal(root.get("status"), filter.status()));
    }
}
