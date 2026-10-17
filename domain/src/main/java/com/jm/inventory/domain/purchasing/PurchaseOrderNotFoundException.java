package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class PurchaseOrderNotFoundException extends NotFoundException {

    public PurchaseOrderNotFoundException(UUID id) {
        super("Purchase order " + id + " not found");
    }

    @Override
    public String code() {
        return "PURCHASE_ORDER_NOT_FOUND";
    }
}
