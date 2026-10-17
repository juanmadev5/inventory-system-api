package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class SalesOrderNotEditableException extends ConflictException {

    public SalesOrderNotEditableException(UUID salesOrderId) {
        super("Sales order " + salesOrderId + " is not editable - it is no longer DRAFT");
    }

    @Override
    public String code() {
        return "SALES_ORDER_NOT_EDITABLE";
    }
}
