package com.jm.inventory.infrastructure.sales.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SalesOrderSpringDataRepository
        extends JpaRepository<SalesOrderJpaEntity, UUID>, JpaSpecificationExecutor<SalesOrderJpaEntity> {
}
