package com.jm.inventory.domain.supplier;

import com.jm.inventory.domain.common.ConflictException;

public class SupplierNameAlreadyExistsException extends ConflictException {

    public SupplierNameAlreadyExistsException(String name) {
        super("A supplier named '" + name + "' already exists");
    }

    @Override
    public String code() {
        return "SUPPLIER_NAME_ALREADY_EXISTS";
    }
}
