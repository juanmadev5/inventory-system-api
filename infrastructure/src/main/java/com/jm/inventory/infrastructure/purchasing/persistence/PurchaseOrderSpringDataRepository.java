package com.jm.inventory.infrastructure.purchasing.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PurchaseOrderSpringDataRepository
        extends JpaRepository<PurchaseOrderJpaEntity, UUID>, JpaSpecificationExecutor<PurchaseOrderJpaEntity> {
}
