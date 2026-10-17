package com.jm.inventory.infrastructure.alerting.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Custom-query-only repository (extends the bare {@code Repository} marker, not
 * {@code JpaRepository}) - there is no single entity this naturally belongs to, since the
 * query joins {@code ReorderThresholdJpaEntity} against {@code StockLevelJpaEntity} by hand
 * (an explicit JPQL {@code on} join between two otherwise-unrelated entities, not a mapped
 * association).
 */
public interface StockAlertSpringDataRepository extends Repository<ReorderThresholdJpaEntity, UUID> {

    @Query(value = """
            select new com.jm.inventory.infrastructure.alerting.persistence.StockAlertProjection(
                t.id, t.productId, t.warehouseId, sl.quantityOnHand, t.reorderPoint, t.reorderQuantity)
            from ReorderThresholdJpaEntity t
            left join StockLevelJpaEntity sl
                on sl.productId = t.productId and sl.warehouseId = t.warehouseId
            where (sl.quantityOnHand is null or sl.quantityOnHand <= t.reorderPoint)
              and (:warehouseId is null or t.warehouseId = :warehouseId)
            """,
            countQuery = """
            select count(t)
            from ReorderThresholdJpaEntity t
            left join StockLevelJpaEntity sl
                on sl.productId = t.productId and sl.warehouseId = t.warehouseId
            where (sl.quantityOnHand is null or sl.quantityOnHand <= t.reorderPoint)
              and (:warehouseId is null or t.warehouseId = :warehouseId)
            """)
    Page<StockAlertProjection> findAlerts(@Param("warehouseId") UUID warehouseId, Pageable pageable);
}
