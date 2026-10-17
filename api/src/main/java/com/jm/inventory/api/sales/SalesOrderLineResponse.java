package com.jm.inventory.api.sales;

import com.jm.inventory.domain.sales.SalesOrderLine;
import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderLineResponse(
        UUID id, UUID productId, int quantityOrdered, int quantityShipped, BigDecimal unitPrice) {

    public static SalesOrderLineResponse from(SalesOrderLine line) {
        return new SalesOrderLineResponse(
                line.getId(), line.getProductId(), line.getQuantityOrdered(), line.getQuantityShipped(),
                line.getUnitPrice());
    }
}
