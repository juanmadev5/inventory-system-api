package com.jm.inventory.application.purchasing;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseOrderLineCommand(UUID productId, int quantityOrdered, BigDecimal unitCost) {
}
