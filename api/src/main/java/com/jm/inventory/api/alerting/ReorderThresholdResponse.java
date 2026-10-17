package com.jm.inventory.api.alerting;

import com.jm.inventory.domain.alerting.ReorderThreshold;
import java.util.UUID;

public record ReorderThresholdResponse(UUID id, UUID productId, UUID warehouseId, int reorderPoint, int reorderQuantity) {

    public static ReorderThresholdResponse from(ReorderThreshold reorderThreshold) {
        return new ReorderThresholdResponse(
                reorderThreshold.getId(), reorderThreshold.getProductId(), reorderThreshold.getWarehouseId(),
                reorderThreshold.getReorderPoint(), reorderThreshold.getReorderQuantity());
    }
}
