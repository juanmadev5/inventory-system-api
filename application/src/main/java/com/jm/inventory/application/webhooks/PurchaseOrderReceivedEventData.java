package com.jm.inventory.application.webhooks;

import java.util.UUID;

public record PurchaseOrderReceivedEventData(UUID purchaseOrderId, UUID supplierId, UUID warehouseId) {
}
