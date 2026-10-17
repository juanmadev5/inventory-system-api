package com.jm.inventory.domain.cyclecount;

import java.util.UUID;

public record CycleCountFilter(UUID warehouseId, CycleCountStatus status) {
}
