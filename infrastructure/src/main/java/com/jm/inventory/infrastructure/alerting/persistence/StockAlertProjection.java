package com.jm.inventory.infrastructure.alerting.persistence;

import java.util.UUID;
import lombok.Getter;

/**
 * The JPQL constructor-expression target for {@code StockAlertSpringDataRepository} - field
 * order must match the {@code select new ...(...)} argument order exactly.
 * {@code quantityOnHand} is boxed/nullable because the query's {@code left join} leaves it
 * {@code null} when no {@code StockLevel} row exists yet for the pair - the adapter, not
 * this class, decides that means zero.
 */
@Getter
public class StockAlertProjection {

    private final UUID id;
    private final UUID productId;
    private final UUID warehouseId;
    private final Integer quantityOnHand;
    private final int reorderPoint;
    private final int reorderQuantity;

    public StockAlertProjection(
            UUID id, UUID productId, UUID warehouseId, Integer quantityOnHand, int reorderPoint,
            int reorderQuantity) {
        this.id = id;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantityOnHand = quantityOnHand;
        this.reorderPoint = reorderPoint;
        this.reorderQuantity = reorderQuantity;
    }
}
