package com.jm.inventory.infrastructure.sales.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderLineSpringDataRepository extends JpaRepository<SalesOrderLineJpaEntity, UUID> {

    List<SalesOrderLineJpaEntity> findBySalesOrderId(UUID salesOrderId);
}
