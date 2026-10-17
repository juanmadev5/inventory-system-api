package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class PurchaseOrderLineNotFoundException extends NotFoundException {

    public PurchaseOrderLineNotFoundException(UUID lineId) {
        super("Purchase order line " + lineId + " not found");
    }

    @Override
    public String code() {
        return "PURCHASE_ORDER_LINE_NOT_FOUND";
    }
}
