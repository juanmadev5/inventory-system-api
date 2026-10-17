package com.jm.inventory.api.reporting;

import com.jm.inventory.application.reporting.StockAgingRow;
import java.time.Instant;
import java.util.UUID;

public record StockAgingRowResponse(
        UUID productId, UUID warehouseId, int quantityOnHand, Instant lastMovementAt, Long daysSinceLastMovement) {

    public static StockAgingRowResponse from(StockAgingRow row) {
        return new StockAgingRowResponse(
                row.productId(), row.warehouseId(), row.quantityOnHand(), row.lastMovementAt(), row.daysSinceLastMovement());
    }
}
