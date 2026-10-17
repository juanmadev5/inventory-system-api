package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class SalesOrderLineNotFoundException extends NotFoundException {

    public SalesOrderLineNotFoundException(UUID lineId) {
        super("Sales order line " + lineId + " not found");
    }

    @Override
    public String code() {
        return "SALES_ORDER_LINE_NOT_FOUND";
    }
}
