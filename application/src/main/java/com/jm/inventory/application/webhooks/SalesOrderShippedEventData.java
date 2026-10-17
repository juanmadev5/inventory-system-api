package com.jm.inventory.application.webhooks;

import java.util.UUID;

public record SalesOrderShippedEventData(UUID salesOrderId, UUID customerId, UUID warehouseId) {
}
