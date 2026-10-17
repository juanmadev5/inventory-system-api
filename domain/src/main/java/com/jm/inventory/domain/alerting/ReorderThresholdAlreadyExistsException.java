package com.jm.inventory.domain.alerting;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class ReorderThresholdAlreadyExistsException extends ConflictException {

    public ReorderThresholdAlreadyExistsException(UUID productId, UUID warehouseId) {
        super("A reorder threshold for product " + productId + " at warehouse " + warehouseId + " already exists");
    }

    @Override
    public String code() {
        return "REORDER_THRESHOLD_ALREADY_EXISTS";
    }
}
