package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(UUID productId, UUID warehouseId) {
        super("Not enough stock of product " + productId + " at warehouse " + warehouseId
                + " for this movement");
    }

    @Override
    public String code() {
        return "INSUFFICIENT_STOCK";
    }
}
