package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class SalesOrderNotFoundException extends NotFoundException {

    public SalesOrderNotFoundException(UUID id) {
        super("Sales order " + id + " not found");
    }

    @Override
    public String code() {
        return "SALES_ORDER_NOT_FOUND";
    }
}
