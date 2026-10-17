package com.jm.inventory.api.purchasing;

import com.jm.inventory.domain.purchasing.PurchaseOrderLine;
import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderLineResponse(
        UUID id, UUID productId, int quantityOrdered, int quantityReceived, BigDecimal unitCost) {

    public static PurchaseOrderLineResponse from(PurchaseOrderLine line) {
        return new PurchaseOrderLineResponse(
                line.getId(), line.getProductId(), line.getQuantityOrdered(), line.getQuantityReceived(),
                line.getUnitCost());
    }
}
