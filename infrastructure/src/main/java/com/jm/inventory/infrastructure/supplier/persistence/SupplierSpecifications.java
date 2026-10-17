package com.jm.inventory.infrastructure.supplier.persistence;

import com.jm.inventory.domain.supplier.SupplierFilter;
import org.springframework.data.jpa.domain.Specification;

public final class SupplierSpecifications {

    private SupplierSpecifications() {
    }

    public static Specification<SupplierJpaEntity> matching(SupplierFilter filter) {
        return Specification.<SupplierJpaEntity>allOf((root, query, builder) -> filter.name() == null
                ? null
                : builder.like(builder.lower(root.get("name")), "%" + filter.name().toLowerCase() + "%"));
    }
}
