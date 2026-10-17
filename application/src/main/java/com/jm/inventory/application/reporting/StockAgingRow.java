package com.jm.inventory.application.reporting;

import java.time.Instant;
import java.util.UUID;

/** {@code lastMovementAt}/{@code daysSinceLastMovement} are {@code null} when this pair has
 * never had a movement - see docs/specs/10-reporting/requirements.md. */
public record StockAgingRow(
        UUID productId, UUID warehouseId, int quantityOnHand, Instant lastMovementAt, Long daysSinceLastMovement) {
}
