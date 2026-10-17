package com.jm.inventory.infrastructure.purchasing.persistence;

import com.jm.inventory.domain.purchasing.PurchaseOrderFilter;
import org.springframework.data.jpa.domain.Specification;

public final class PurchaseOrderSpecifications {

    private PurchaseOrderSpecifications() {
    }

    public static Specification<PurchaseOrderJpaEntity> matching(PurchaseOrderFilter filter) {
        return Specification.<PurchaseOrderJpaEntity>allOf(
                (root, query, builder) -> filter.supplierId() == null
                        ? null
                        : builder.equal(root.get("supplierId"), filter.supplierId()),
                (root, query, builder) -> filter.warehouseId() == null
                        ? null
                        : builder.equal(root.get("warehouseId"), filter.warehouseId()),
                (root, query, builder) -> filter.status() == null
                        ? null
                        : builder.equal(root.get("status"), filter.status()));
    }
}
