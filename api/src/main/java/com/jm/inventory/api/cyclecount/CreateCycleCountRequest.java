package com.jm.inventory.api.cyclecount;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * {@code productIds} omitted/{@code null} counts every product with stock at the warehouse
 * (FULL); provided, only those (SELECTION) - see
 * docs/specs/09-cycle-counts/requirements.md's "Decision".
 */
public record CreateCycleCountRequest(@NotNull UUID warehouseId, List<UUID> productIds) {
}
