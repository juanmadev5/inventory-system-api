package com.jm.inventory.api.stock;

import com.jm.inventory.domain.stock.StockLevel;
import java.util.UUID;

public record StockLevelResponse(
        UUID id, UUID productId, UUID warehouseId, int quantityOnHand, int quantityReserved) {

    public static StockLevelResponse from(StockLevel stockLevel) {
        return new StockLevelResponse(
                stockLevel.getId(),
                stockLevel.getProductId(),
                stockLevel.getWarehouseId(),
                stockLevel.getQuantityOnHand(),
                stockLevel.getQuantityReserved());
    }
}
