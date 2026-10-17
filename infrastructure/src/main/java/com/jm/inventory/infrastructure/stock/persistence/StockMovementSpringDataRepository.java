package com.jm.inventory.infrastructure.stock.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementSpringDataRepository
        extends JpaRepository<StockMovementJpaEntity, UUID>, JpaSpecificationExecutor<StockMovementJpaEntity> {

    /**
     * {@code null} (via a single-row {@code max()} with no matches) if this pair has never
     * had a movement - see docs/specs/10-reporting/requirements.md.
     */
    @Query("select max(m.occurredAt) from StockMovementJpaEntity m "
            + "where m.productId = :productId and m.warehouseId = :warehouseId")
    Instant findMostRecentOccurredAt(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);
}
