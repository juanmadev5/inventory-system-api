package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class PurchaseOrderNotCancellableException extends ConflictException {

    public PurchaseOrderNotCancellableException(UUID purchaseOrderId) {
        super("Purchase order " + purchaseOrderId + " can no longer be cancelled");
    }

    @Override
    public String code() {
        return "PURCHASE_ORDER_NOT_CANCELLABLE";
    }
}
