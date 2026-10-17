package com.jm.inventory.api.stock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateStockTransferRequest(
        @NotNull UUID productId,
        @NotNull UUID sourceWarehouseId,
        @NotNull UUID destinationWarehouseId,
        @Min(1) int quantity,
        String reference,
        Instant occurredAt) {
}
