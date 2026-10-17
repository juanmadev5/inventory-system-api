package com.jm.inventory.application.sales;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSalesOrderLineCommand(UUID productId, int quantityOrdered, BigDecimal unitPrice) {
}
