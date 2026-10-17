package com.jm.inventory.infrastructure.catalog.persistence;

import com.jm.inventory.domain.catalog.CategoryFilter;
import org.springframework.data.jpa.domain.Specification;

public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    public static Specification<CategoryJpaEntity> matching(CategoryFilter filter) {
        return Specification.<CategoryJpaEntity>allOf(
                (root, query, builder) -> filter.name() == null
                        ? null
                        : builder.like(builder.lower(root.get("name")), "%" + filter.name().toLowerCase() + "%"),
                (root, query, builder) -> filter.parentCategoryId() == null
                        ? null
                        : builder.equal(root.get("parentCategoryId"), filter.parentCategoryId()));
    }
}
