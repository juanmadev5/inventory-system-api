package com.jm.inventory.infrastructure.supplier.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierSpringDataRepository
        extends JpaRepository<SupplierJpaEntity, UUID>, JpaSpecificationExecutor<SupplierJpaEntity> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
