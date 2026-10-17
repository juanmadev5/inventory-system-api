package com.jm.inventory.domain.sales;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Value;

@Value
public class SalesOrderLine {
    UUID id;
    UUID productId;
    int quantityOrdered;
    int quantityShipped;
    BigDecimal unitPrice;
}
