package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository {

    StockMovement save(StockMovement movement);

    Optional<StockMovement> findById(UUID id);

    PagedResult<StockMovement> findAll(StockMovementFilter filter, int page, int pageSize);

    /**
     * Every matching movement, unpaginated - used by reporting
     * (docs/specs/10-reporting/requirements.md), which aggregates across a whole filtered
     * range rather than one page of it. Never used by a client-facing list endpoint, which
     * must stay paginated per docs/conventions/api-design.md.
     */
    List<StockMovement> findAllMatching(StockMovementFilter filter);

    /** {@code empty()} if this pair has never had a movement. */
    Optional<Instant> findMostRecentOccurredAt(UUID productId, UUID warehouseId);
}
