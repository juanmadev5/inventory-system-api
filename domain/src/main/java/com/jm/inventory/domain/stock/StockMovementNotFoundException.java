package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class StockMovementNotFoundException extends NotFoundException {

    public StockMovementNotFoundException(UUID id) {
        super("Stock movement " + id + " not found");
    }

    @Override
    public String code() {
        return "STOCK_MOVEMENT_NOT_FOUND";
    }
}
