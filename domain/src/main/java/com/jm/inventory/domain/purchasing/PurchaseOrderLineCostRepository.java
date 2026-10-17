package com.jm.inventory.domain.purchasing;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * A narrow, reporting-only port (docs/specs/10-reporting/requirements.md) - separate from
 * {@link PurchaseOrderRepository} since it queries raw lines across every order for a
 * product, not a single order aggregate.
 */
public interface PurchaseOrderLineCostRepository {

    /** {@code empty()} if the product has never appeared on any purchase order line. */
    Optional<BigDecimal> averageUnitCostForProduct(UUID productId);
}
