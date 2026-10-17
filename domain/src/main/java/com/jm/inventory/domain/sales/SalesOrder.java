package com.jm.inventory.domain.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Value;

@Value
public class SalesOrder {
    UUID id;
    UUID customerId;
    UUID warehouseId;
    SalesOrderStatus status;
    LocalDate expectedShipDate;
    List<SalesOrderLine> lines;
}
