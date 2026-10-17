package com.jm.inventory.domain.purchasing;

import java.util.UUID;

public record PurchaseOrderFilter(UUID supplierId, UUID warehouseId, PurchaseOrderStatus status) {
}
