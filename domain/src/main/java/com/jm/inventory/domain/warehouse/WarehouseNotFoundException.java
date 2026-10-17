package com.jm.inventory.domain.warehouse;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class WarehouseNotFoundException extends NotFoundException {

    public WarehouseNotFoundException(UUID id) {
        super("Warehouse " + id + " not found");
    }

    @Override
    public String code() {
        return "WAREHOUSE_NOT_FOUND";
    }
}
