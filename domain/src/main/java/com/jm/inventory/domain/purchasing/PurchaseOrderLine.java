package com.jm.inventory.domain.purchasing;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Value;

@Value
public class PurchaseOrderLine {
    UUID id;
    UUID productId;
    int quantityOrdered;
    int quantityReceived;
    BigDecimal unitCost;
}
