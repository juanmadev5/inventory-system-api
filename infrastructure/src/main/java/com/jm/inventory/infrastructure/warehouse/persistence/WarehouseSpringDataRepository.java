package com.jm.inventory.infrastructure.warehouse.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WarehouseSpringDataRepository
        extends JpaRepository<WarehouseJpaEntity, UUID>, JpaSpecificationExecutor<WarehouseJpaEntity> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
