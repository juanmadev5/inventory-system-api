package com.jm.inventory.api.stock;

import com.jm.inventory.domain.stock.StockMovement;
import com.jm.inventory.domain.stock.StockMovementType;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID productId,
        UUID warehouseId,
        StockMovementType type,
        int quantity,
        Instant occurredAt,
        String reference,
        UUID relatedMovementId) {

    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getProductId(),
                movement.getWarehouseId(),
                movement.getType(),
                movement.getQuantity(),
                movement.getOccurredAt(),
                movement.getReference(),
                movement.getRelatedMovementId());
    }
}
