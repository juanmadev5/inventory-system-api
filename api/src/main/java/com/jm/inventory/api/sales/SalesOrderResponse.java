package com.jm.inventory.api.sales;

import com.jm.inventory.domain.sales.SalesOrder;
import com.jm.inventory.domain.sales.SalesOrderStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesOrderResponse(
        UUID id,
        UUID customerId,
        UUID warehouseId,
        SalesOrderStatus status,
        LocalDate expectedShipDate,
        List<SalesOrderLineResponse> lines) {

    public static SalesOrderResponse from(SalesOrder salesOrder) {
        return new SalesOrderResponse(
                salesOrder.getId(),
                salesOrder.getCustomerId(),
                salesOrder.getWarehouseId(),
                salesOrder.getStatus(),
                salesOrder.getExpectedShipDate(),
                salesOrder.getLines().stream().map(SalesOrderLineResponse::from).toList());
    }
}
