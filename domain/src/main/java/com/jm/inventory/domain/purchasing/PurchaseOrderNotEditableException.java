package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class PurchaseOrderNotEditableException extends ConflictException {

    public PurchaseOrderNotEditableException(UUID purchaseOrderId) {
        super("Purchase order " + purchaseOrderId + " is not editable - it is no longer DRAFT");
    }

    @Override
    public String code() {
        return "PURCHASE_ORDER_NOT_EDITABLE";
    }
}
