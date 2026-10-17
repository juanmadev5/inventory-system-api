package com.jm.inventory.infrastructure.alerting.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReorderThresholdSpringDataRepository
        extends JpaRepository<ReorderThresholdJpaEntity, UUID>, JpaSpecificationExecutor<ReorderThresholdJpaEntity> {

    Optional<ReorderThresholdJpaEntity> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);
}
