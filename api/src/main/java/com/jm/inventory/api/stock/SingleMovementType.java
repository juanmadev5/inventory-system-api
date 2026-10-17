package com.jm.inventory.api.stock;

import com.jm.inventory.domain.stock.StockMovementType;

/**
 * The subset of {@link StockMovementType} creatable via {@code POST /api/v1/stock-movements}
 * directly - {@code TRANSFER_IN}/{@code TRANSFER_OUT} only come from the transfer endpoint,
 * since a lone half of a transfer pair is never valid. An unrecognized value (including
 * either transfer type) fails Jackson deserialization with {@code 400}, never reaching a
 * business-rule check - see docs/specs/02-stock-movements/requirements.md.
 */
public enum SingleMovementType {
    RECEIPT,
    SHIPMENT,
    ADJUSTMENT_INCREASE,
    ADJUSTMENT_DECREASE;

    public StockMovementType toDomain() {
        return StockMovementType.valueOf(name());
    }

    /**
     * A bare adjustment has no purchase order, sales order, or cycle count behind it - per
     * docs/specs/06-roles-and-permissions/requirements.md, that is the one case within this
     * single endpoint that requires {@code ADMIN} rather than {@code STAFF}.
     */
    public boolean requiresAdminRole() {
        return this == ADJUSTMENT_INCREASE || this == ADJUSTMENT_DECREASE;
    }
}
