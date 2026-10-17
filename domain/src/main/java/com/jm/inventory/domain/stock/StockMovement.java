package com.jm.inventory.domain.stock;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;

/**
 * Immutable once created - an append-only ledger entry, never updated or deleted. See
 * docs/specs/02-stock-movements/requirements.md.
 */
@Value
public class StockMovement {
    UUID id;
    UUID productId;
    UUID warehouseId;
    StockMovementType type;
    int quantity;
    Instant occurredAt;
    String reference;
    UUID relatedMovementId;
}
