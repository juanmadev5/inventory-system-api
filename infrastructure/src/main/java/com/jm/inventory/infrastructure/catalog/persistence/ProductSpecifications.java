package com.jm.inventory.infrastructure.catalog.persistence;

import com.jm.inventory.domain.catalog.ProductFilter;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<ProductJpaEntity> matching(ProductFilter filter) {
        return Specification.<ProductJpaEntity>allOf(
                (root, query, builder) -> filter.categoryId() == null
                        ? null
                        : builder.equal(root.get("categoryId"), filter.categoryId()),
                (root, query, builder) -> filter.sku() == null
                        ? null
                        : builder.equal(root.get("sku"), filter.sku()),
                (root, query, builder) -> filter.active() == null
                        ? null
                        : builder.equal(root.get("active"), filter.active()));
    }
}
