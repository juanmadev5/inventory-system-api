package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class ReceiptExceedsOrderedQuantityException extends ConflictException {

    public ReceiptExceedsOrderedQuantityException(UUID lineId) {
        super("This receipt would exceed line " + lineId + "'s ordered quantity");
    }

    @Override
    public String code() {
        return "RECEIPT_EXCEEDS_ORDERED_QUANTITY";
    }
}
