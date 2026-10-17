package com.jm.inventory.infrastructure.customer.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerSpringDataRepository
        extends JpaRepository<CustomerJpaEntity, UUID>, JpaSpecificationExecutor<CustomerJpaEntity> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
