package com.jm.inventory.domain.cyclecount;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class CycleCountNotFoundException extends NotFoundException {

    public CycleCountNotFoundException(UUID id) {
        super("Cycle count " + id + " not found");
    }

    @Override
    public String code() {
        return "CYCLE_COUNT_NOT_FOUND";
    }
}
