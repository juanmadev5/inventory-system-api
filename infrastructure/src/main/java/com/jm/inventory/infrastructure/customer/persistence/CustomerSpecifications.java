package com.jm.inventory.infrastructure.customer.persistence;

import com.jm.inventory.domain.customer.CustomerFilter;
import org.springframework.data.jpa.domain.Specification;

public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    public static Specification<CustomerJpaEntity> matching(CustomerFilter filter) {
        return Specification.<CustomerJpaEntity>allOf((root, query, builder) -> filter.name() == null
                ? null
                : builder.like(builder.lower(root.get("name")), "%" + filter.name().toLowerCase() + "%"));
    }
}
