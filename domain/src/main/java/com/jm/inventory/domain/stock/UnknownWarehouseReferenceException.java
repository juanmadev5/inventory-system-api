package com.jm.inventory.domain.stock;

import com.jm.inventory.domain.common.InvalidReferenceException;
import java.util.UUID;

/**
 * {@code field} is which request field named the unknown warehouse - {@code "warehouseId"}
 * for a single movement, {@code "sourceWarehouseId"}/{@code "destinationWarehouseId"} for a
 * transfer.
 */
public class UnknownWarehouseReferenceException extends InvalidReferenceException {

    public UnknownWarehouseReferenceException(UUID warehouseId, String field) {
        super(field, "Warehouse " + warehouseId + " does not exist");
    }
}
