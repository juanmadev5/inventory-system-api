package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class SalesOrderNotConfirmedException extends ConflictException {

    public SalesOrderNotConfirmedException(UUID salesOrderId) {
        super("Sales order " + salesOrderId + " is not CONFIRMED - cannot ship against it");
    }

    @Override
    public String code() {
        return "SALES_ORDER_NOT_CONFIRMED";
    }
}
