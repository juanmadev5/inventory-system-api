package com.jm.inventory.api.warehouse;

import com.jm.inventory.domain.warehouse.Warehouse;
import java.util.UUID;

public record WarehouseResponse(UUID id, String name, String address, boolean active) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getName(), warehouse.getAddress(), warehouse.isActive());
    }
}
