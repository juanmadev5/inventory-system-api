package com.jm.inventory.domain.alerting;

import java.util.UUID;
import lombok.Value;

@Value
public class ReorderThreshold {
    UUID id;
    UUID productId;
    UUID warehouseId;
    int reorderPoint;
    int reorderQuantity;
}
