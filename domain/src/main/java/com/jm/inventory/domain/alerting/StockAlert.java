package com.jm.inventory.domain.alerting;

import java.util.UUID;
import lombok.Value;

/**
 * Not persisted on its own - a read-only projection joining every configured
 * {@link ReorderThreshold} against its current {@code StockLevel.quantityOnHand} (0 if no
 * stock level row exists yet for that pair). {@code id} is the underlying threshold's id,
 * since each alert maps 1:1 to one.
 */
@Value
public class StockAlert {
    UUID id;
    UUID productId;
    UUID warehouseId;
    int quantityOnHand;
    int reorderPoint;
    int reorderQuantity;
}
