package com.jm.inventory.api.alerting;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateReorderThresholdRequest(
        @NotNull UUID productId,
        @NotNull UUID warehouseId,
        @Min(0) int reorderPoint,
        @Min(1) int reorderQuantity) {
}
