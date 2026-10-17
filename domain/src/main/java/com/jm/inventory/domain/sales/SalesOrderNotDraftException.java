package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class SalesOrderNotDraftException extends ConflictException {

    public SalesOrderNotDraftException(UUID salesOrderId) {
        super("Sales order " + salesOrderId + " is not DRAFT and cannot be confirmed");
    }

    @Override
    public String code() {
        return "SALES_ORDER_NOT_DRAFT";
    }
}
