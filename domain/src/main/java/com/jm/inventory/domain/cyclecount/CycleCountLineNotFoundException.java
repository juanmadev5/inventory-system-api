package com.jm.inventory.domain.cyclecount;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class CycleCountLineNotFoundException extends NotFoundException {

    public CycleCountLineNotFoundException(UUID lineId) {
        super("Cycle count line " + lineId + " not found");
    }

    @Override
    public String code() {
        return "CYCLE_COUNT_LINE_NOT_FOUND";
    }
}
