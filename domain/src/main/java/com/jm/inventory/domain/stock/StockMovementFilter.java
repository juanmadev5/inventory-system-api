package com.jm.inventory.domain.stock;

import java.time.Instant;
import java.util.UUID;

public record StockMovementFilter(
        UUID productId, UUID warehouseId, StockMovementType type, Instant occurredFrom, Instant occurredTo) {
}
