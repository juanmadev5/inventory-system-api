package com.jm.inventory.api.purchasing;

import com.jm.inventory.domain.purchasing.PurchaseOrder;
import com.jm.inventory.domain.purchasing.PurchaseOrderStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        UUID supplierId,
        UUID warehouseId,
        PurchaseOrderStatus status,
        LocalDate expectedDeliveryDate,
        List<PurchaseOrderLineResponse> lines) {

    public static PurchaseOrderResponse from(PurchaseOrder purchaseOrder) {
        return new PurchaseOrderResponse(
                purchaseOrder.getId(),
                purchaseOrder.getSupplierId(),
                purchaseOrder.getWarehouseId(),
                purchaseOrder.getStatus(),
                purchaseOrder.getExpectedDeliveryDate(),
                purchaseOrder.getLines().stream().map(PurchaseOrderLineResponse::from).toList());
    }
}
