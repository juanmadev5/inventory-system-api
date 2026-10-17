package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.InvalidReferenceException;
import java.util.UUID;

public class SameWarehouseTransferException extends InvalidReferenceException {

    public SameWarehouseTransferException(UUID warehouseId) {
        super("destinationWarehouseId", "Source and destination warehouse must be different (both are "
                + warehouseId + ")");
    }
}
