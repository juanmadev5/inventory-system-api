package com.jm.inventory.api.cyclecount;

import com.jm.inventory.domain.cyclecount.CycleCount;
import com.jm.inventory.domain.cyclecount.CycleCountScope;
import com.jm.inventory.domain.cyclecount.CycleCountStatus;
import java.util.List;
import java.util.UUID;

public record CycleCountResponse(
        UUID id,
        UUID warehouseId,
        CycleCountScope scope,
        CycleCountStatus status,
        List<CycleCountLineResponse> lines) {

    public static CycleCountResponse from(CycleCount cycleCount) {
        return new CycleCountResponse(
                cycleCount.getId(),
                cycleCount.getWarehouseId(),
                cycleCount.getScope(),
                cycleCount.getStatus(),
                cycleCount.getLines().stream().map(CycleCountLineResponse::from).toList());
    }
}
