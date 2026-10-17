package com.jm.inventory.api.sales;

import jakarta.validation.constraints.Min;

public record ShipSalesOrderLineRequest(@Min(1) int quantity) {
}
