package com.jm.inventory.domain.stock;

/**
 * Each value carries its own fixed sign - "quantity is always positive, direction comes
 * from type" (docs/specs/02-stock-movements/requirements.md) is enforced by the type
 * system here, never a separate direction field a caller could set inconsistently.
 */
public enum StockMovementType {
    RECEIPT(1),
    SHIPMENT(-1),
    TRANSFER_IN(1),
    TRANSFER_OUT(-1),
    ADJUSTMENT_INCREASE(1),
    ADJUSTMENT_DECREASE(-1);

    private final int sign;

    StockMovementType(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }
}
