package com.jm.inventory.api.alerting;

import com.jm.inventory.domain.alerting.StockAlert;
import java.util.UUID;

public record StockAlertResponse(
        UUID id, UUID productId, UUID warehouseId, int quantityOnHand, int reorderPoint, int reorderQuantity) {

    public static StockAlertResponse from(StockAlert stockAlert) {
        return new StockAlertResponse(
                stockAlert.getId(), stockAlert.getProductId(), stockAlert.getWarehouseId(),
                stockAlert.getQuantityOnHand(), stockAlert.getReorderPoint(), stockAlert.getReorderQuantity());
    }
}
