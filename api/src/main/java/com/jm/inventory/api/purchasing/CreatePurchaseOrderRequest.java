package com.jm.inventory.api.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @NotNull UUID supplierId,
        @NotNull UUID warehouseId,
        LocalDate expectedDeliveryDate,
        @NotEmpty @Valid List<CreatePurchaseOrderLineRequest> lines) {
}
