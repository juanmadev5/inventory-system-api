package com.jm.inventory.application.warehouse;

import com.jm.inventory.domain.stock.UnknownWarehouseReferenceException;
import com.jm.inventory.domain.stock.WarehouseInactiveException;
import com.jm.inventory.domain.warehouse.Warehouse;
import com.jm.inventory.domain.warehouse.WarehouseRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The "does this warehouse exist, and is it active" check every feature that writes
 * against a warehouse needs - shared so the rule (and its two exception types) lives in
 * exactly one place. First extracted here when
 * docs/specs/03-suppliers-and-purchase-orders/ needed the exact same check
 * {@code StockMovementRecordingService} already had.
 */
@Component
public class ActiveWarehouseValidator {

    private final WarehouseRepository warehouseRepository;

    public ActiveWarehouseValidator(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    public Warehouse requireActive(UUID warehouseId, String field) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new UnknownWarehouseReferenceException(warehouseId, field));
        if (!warehouse.isActive()) {
            throw new WarehouseInactiveException(warehouseId);
        }
        return warehouse;
    }
}
