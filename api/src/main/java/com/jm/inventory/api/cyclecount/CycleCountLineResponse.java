package com.jm.inventory.api.cyclecount;

import com.jm.inventory.domain.cyclecount.CycleCountLine;
import java.util.UUID;

public record CycleCountLineResponse(UUID id, UUID productId, Integer countedQuantity) {

    public static CycleCountLineResponse from(CycleCountLine line) {
        return new CycleCountLineResponse(line.getId(), line.getProductId(), line.getCountedQuantity());
    }
}
