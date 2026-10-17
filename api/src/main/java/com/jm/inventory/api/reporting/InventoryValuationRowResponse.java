package com.jm.inventory.api.reporting;

import com.jm.inventory.application.reporting.InventoryValuationRow;
import java.math.BigDecimal;
import java.util.UUID;

public record InventoryValuationRowResponse(
        UUID productId, UUID warehouseId, UUID categoryId, int quantityOnHand, BigDecimal unitCost, BigDecimal value) {

    public static InventoryValuationRowResponse from(InventoryValuationRow row) {
        return new InventoryValuationRowResponse(
                row.productId(), row.warehouseId(), row.categoryId(), row.quantityOnHand(), row.unitCost(), row.value());
    }
}
