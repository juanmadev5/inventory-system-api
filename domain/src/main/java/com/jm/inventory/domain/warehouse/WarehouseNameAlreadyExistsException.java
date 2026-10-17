package com.jm.inventory.domain.warehouse;

import com.jm.inventory.domain.common.ConflictException;

public class WarehouseNameAlreadyExistsException extends ConflictException {

    public WarehouseNameAlreadyExistsException(String name) {
        super("A warehouse named '" + name + "' already exists");
    }

    @Override
    public String code() {
        return "WAREHOUSE_NAME_ALREADY_EXISTS";
    }
}
