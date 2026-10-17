package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class WarehouseInactiveException extends ConflictException {

    public WarehouseInactiveException(UUID warehouseId) {
        super("Warehouse " + warehouseId + " is inactive");
    }

    @Override
    public String code() {
        return "WAREHOUSE_INACTIVE";
    }
}
