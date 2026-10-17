package com.jm.inventory.domain.alerting;

import java.util.UUID;

public record ReorderThresholdFilter(UUID productId, UUID warehouseId) {
}
