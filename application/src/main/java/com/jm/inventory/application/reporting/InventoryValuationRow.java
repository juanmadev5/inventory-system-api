package com.jm.inventory.application.reporting;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code unitCost}/{@code value} are {@code null} when the product has no purchase-order
 * cost history - see docs/specs/10-reporting/requirements.md. */
public record InventoryValuationRow(
        UUID productId, UUID warehouseId, UUID categoryId, int quantityOnHand, BigDecimal unitCost, BigDecimal value) {
}
