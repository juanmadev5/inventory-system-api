package com.jm.inventory.infrastructure.stock.persistence;

import com.jm.inventory.domain.stock.StockLevelFilter;
import org.springframework.data.jpa.domain.Specification;

public final class StockLevelSpecifications {

    private StockLevelSpecifications() {
    }

    public static Specification<StockLevelJpaEntity> matching(StockLevelFilter filter) {
        return Specification.<StockLevelJpaEntity>allOf(
                (root, query, builder) -> filter.productId() == null
                        ? null
                        : builder.equal(root.get("productId"), filter.productId()),
                (root, query, builder) -> filter.warehouseId() == null
                        ? null
                        : builder.equal(root.get("warehouseId"), filter.warehouseId()));
    }
}
