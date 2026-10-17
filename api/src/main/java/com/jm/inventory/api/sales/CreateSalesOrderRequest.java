package com.jm.inventory.api.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateSalesOrderRequest(
        @NotNull UUID customerId,
        @NotNull UUID warehouseId,
        LocalDate expectedShipDate,
        @NotEmpty @Valid List<CreateSalesOrderLineRequest> lines) {
}
