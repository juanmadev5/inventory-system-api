package com.jm.inventory.domain.supplier;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class SupplierNotFoundException extends NotFoundException {

    public SupplierNotFoundException(UUID id) {
        super("Supplier " + id + " not found");
    }

    @Override
    public String code() {
        return "SUPPLIER_NOT_FOUND";
    }
}
