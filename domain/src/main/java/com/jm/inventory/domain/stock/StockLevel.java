package com.jm.inventory.domain.stock;

import java.util.UUID;
import lombok.Value;

/**
 * Read-only in this feature - see docs/specs/01-catalog-and-stock/requirements.md. No
 * constructor here is used to create a row yet; rows start appearing once the Stock
 * Movements feature ships.
 */
@Value
public class StockLevel {
    UUID id;
    UUID productId;
    UUID warehouseId;
    int quantityOnHand;
    int quantityReserved;
}
