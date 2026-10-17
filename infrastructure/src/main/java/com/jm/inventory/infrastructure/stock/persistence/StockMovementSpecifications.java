package com.jm.inventory.infrastructure.stock.persistence;

import com.jm.inventory.domain.stock.StockMovementFilter;
import org.springframework.data.jpa.domain.Specification;

public final class StockMovementSpecifications {

    private StockMovementSpecifications() {
    }

    public static Specification<StockMovementJpaEntity> matching(StockMovementFilter filter) {
        return Specification.<StockMovementJpaEntity>allOf(
                (root, query, builder) -> filter.productId() == null
                        ? null
                        : builder.equal(root.get("productId"), filter.productId()),
                (root, query, builder) -> filter.warehouseId() == null
                        ? null
                        : builder.equal(root.get("warehouseId"), filter.warehouseId()),
                (root, query, builder) -> filter.type() == null
                        ? null
                        : builder.equal(root.get("type"), filter.type()),
                (root, query, builder) -> filter.occurredFrom() == null
                        ? null
                        : builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.occurredFrom()),
                (root, query, builder) -> filter.occurredTo() == null
                        ? null
                        : builder.lessThanOrEqualTo(root.get("occurredAt"), filter.occurredTo()));
    }
}
