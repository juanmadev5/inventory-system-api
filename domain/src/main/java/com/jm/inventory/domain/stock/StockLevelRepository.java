package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLevelRepository {

    Optional<StockLevel> findById(UUID id);

    PagedResult<StockLevel> findAll(StockLevelFilter filter, int page, int pageSize);

    /**
     * Every product with an existing {@code StockLevel} row at this warehouse, active or
     * not - used to seed a full cycle count's lines, see
     * docs/specs/09-cycle-counts/requirements.md.
     */
    List<UUID> findProductIdsByWarehouseId(UUID warehouseId);

    /**
     * Returns the {@code (productId, warehouseId)} row - creating it with a zero quantity
     * first if it doesn't exist yet - locked for the duration of the current transaction
     * (a Postgres {@code SELECT ... FOR UPDATE}), so two concurrent movements against the
     * same pair are serialized rather than both reading the same stale quantity. Callers
     * must be inside a transaction; see docs/specs/02-stock-movements/requirements.md's
     * concurrency criterion.
     */
    StockLevel lockOrCreateForUpdate(UUID productId, UUID warehouseId);

    StockLevel save(StockLevel stockLevel);
}
