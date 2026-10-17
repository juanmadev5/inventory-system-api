package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class PurchaseOrderNotDraftException extends ConflictException {

    public PurchaseOrderNotDraftException(UUID purchaseOrderId) {
        super("Purchase order " + purchaseOrderId + " is not DRAFT and cannot be placed");
    }

    @Override
    public String code() {
        return "PURCHASE_ORDER_NOT_DRAFT";
    }
}
