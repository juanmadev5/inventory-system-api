package com.jm.inventory.application.webhooks;

import java.util.UUID;

public record StockAlertActivatedEventData(UUID productId, UUID warehouseId, int quantityOnHand, int reorderPoint) {
}
