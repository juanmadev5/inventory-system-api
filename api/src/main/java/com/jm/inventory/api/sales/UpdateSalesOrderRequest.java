package com.jm.inventory.api.sales;

import java.time.LocalDate;

public record UpdateSalesOrderRequest(LocalDate expectedShipDate) {
}
