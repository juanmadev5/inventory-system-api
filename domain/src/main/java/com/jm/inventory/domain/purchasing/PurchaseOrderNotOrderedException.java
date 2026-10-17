package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class PurchaseOrderNotOrderedException extends ConflictException {

    public PurchaseOrderNotOrderedException(UUID purchaseOrderId) {
        super("Purchase order " + purchaseOrderId + " is not ORDERED - cannot receive against it");
    }

    @Override
    public String code() {
        return "PURCHASE_ORDER_NOT_ORDERED";
    }
}
