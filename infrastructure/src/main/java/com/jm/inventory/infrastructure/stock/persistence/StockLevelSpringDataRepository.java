package com.jm.inventory.infrastructure.stock.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLevelSpringDataRepository
        extends JpaRepository<StockLevelJpaEntity, UUID>, JpaSpecificationExecutor<StockLevelJpaEntity> {

    /**
     * Atomically creates the zero-quantity row if it doesn't exist yet - safe under
     * concurrent callers, since {@code ON CONFLICT DO NOTHING} on the
     * {@code (product_id, warehouse_id)} unique constraint lets exactly one insert win and
     * every other one silently no-op, rather than racing on a duplicate-key exception.
     */
    @Modifying
    @Query(value = "insert into stock_levels (id, product_id, warehouse_id, quantity_on_hand, "
            + "quantity_reserved) values (:id, :productId, :warehouseId, 0, 0) "
            + "on conflict (product_id, warehouse_id) do nothing",
            nativeQuery = true)
    void insertZeroRowIfAbsent(@Param("id") UUID id, @Param("productId") UUID productId,
            @Param("warehouseId") UUID warehouseId);

    /**
     * {@code SELECT ... FOR UPDATE} - call only after {@link #insertZeroRowIfAbsent} has
     * guaranteed the row exists, and only inside the transaction that will apply a delta to
     * it. See docs/specs/02-stock-movements/requirements.md's concurrency criterion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevelJpaEntity s where s.productId = :productId and s.warehouseId = :warehouseId")
    Optional<StockLevelJpaEntity> findByProductIdAndWarehouseIdForUpdate(
            @Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);

    List<StockLevelJpaEntity> findByWarehouseId(UUID warehouseId);
}
