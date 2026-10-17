package com.jm.inventory.domain.cyclecount;

import java.util.List;
import java.util.UUID;
import lombok.Value;

@Value
public class CycleCount {
    UUID id;
    UUID warehouseId;
    CycleCountScope scope;
    CycleCountStatus status;
    List<CycleCountLine> lines;
}
