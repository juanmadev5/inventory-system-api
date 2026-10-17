package com.jm.inventory.domain.cyclecount;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

public class CycleCountIncompleteException extends ConflictException {

    public CycleCountIncompleteException(UUID cycleCountId) {
        super("Cycle count " + cycleCountId + " has lines with no counted quantity yet");
    }

    @Override
    public String code() {
        return "CYCLE_COUNT_INCOMPLETE";
    }
}
