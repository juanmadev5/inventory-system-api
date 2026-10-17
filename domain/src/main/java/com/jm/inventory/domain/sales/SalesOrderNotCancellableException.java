package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class SalesOrderNotCancellableException extends ConflictException {

    public SalesOrderNotCancellableException(UUID salesOrderId) {
        super("Sales order " + salesOrderId + " can no longer be cancelled");
    }

    @Override
    public String code() {
        return "SALES_ORDER_NOT_CANCELLABLE";
    }
}
