package com.jm.inventory.api.purchasing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseOrderLineRequest(
        @NotNull UUID productId,
        @Min(1) int quantityOrdered,
        @NotNull @DecimalMin("0") BigDecimal unitCost) {
}
