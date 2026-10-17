package com.jm.inventory.domain.sales;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class ShipmentExceedsOrderedQuantityException extends ConflictException {

    public ShipmentExceedsOrderedQuantityException(UUID lineId) {
        super("This shipment would exceed line " + lineId + "'s ordered quantity");
    }

    @Override
    public String code() {
        return "SHIPMENT_EXCEEDS_ORDERED_QUANTITY";
    }
}
