package com.jm.inventory.api.supplier;

import com.jm.inventory.domain.supplier.Supplier;
import java.util.UUID;

public record SupplierResponse(UUID id, String name, String email, String phone) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getEmail(), supplier.getPhone());
    }
}
