package com.jm.inventory.api.cyclecount;

import jakarta.validation.constraints.Min;

public record RecordCycleCountLineRequest(@Min(0) int countedQuantity) {
}
