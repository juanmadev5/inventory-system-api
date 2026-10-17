package com.jm.inventory.domain.purchasing;

import com.jm.inventory.domain.common.InvalidReferenceException;
import java.util.UUID;

public class UnknownSupplierReferenceException extends InvalidReferenceException {

    public UnknownSupplierReferenceException(UUID supplierId) {
        super("supplierId", "Supplier " + supplierId + " does not exist");
    }
}
