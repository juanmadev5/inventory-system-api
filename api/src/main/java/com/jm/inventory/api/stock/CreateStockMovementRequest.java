package com.jm.inventory.api.stock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateStockMovementRequest(
        @NotNull UUID productId,
        @NotNull UUID warehouseId,
        @NotNull SingleMovementType type,
        @Min(1) int quantity,
        String reference,
        Instant occurredAt) {
}
