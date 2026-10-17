package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class StockLevelNotFoundException extends NotFoundException {

    public StockLevelNotFoundException(UUID id) {
        super("Stock level " + id + " not found");
    }

    @Override
    public String code() {
        return "STOCK_LEVEL_NOT_FOUND";
    }
}
