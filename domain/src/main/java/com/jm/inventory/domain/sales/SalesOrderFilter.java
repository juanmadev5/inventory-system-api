package com.jm.inventory.domain.sales;

import java.util.UUID;

public record SalesOrderFilter(UUID customerId, UUID warehouseId, SalesOrderStatus status) {
}
