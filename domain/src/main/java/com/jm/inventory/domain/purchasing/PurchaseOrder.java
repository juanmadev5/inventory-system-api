package com.jm.inventory.domain.purchasing;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Value;

@Value
public class PurchaseOrder {
    UUID id;
    UUID supplierId;
    UUID warehouseId;
    PurchaseOrderStatus status;
    LocalDate expectedDeliveryDate;
    List<PurchaseOrderLine> lines;
}
