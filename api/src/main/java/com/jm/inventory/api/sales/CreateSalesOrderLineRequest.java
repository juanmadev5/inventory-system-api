package com.jm.inventory.api.sales;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateSalesOrderLineRequest(
        @NotNull UUID productId,
        @Min(1) int quantityOrdered,
        @NotNull @DecimalMin("0") BigDecimal unitPrice) {
}
