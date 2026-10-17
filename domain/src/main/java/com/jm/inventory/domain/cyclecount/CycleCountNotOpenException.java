package com.jm.inventory.domain.cyclecount;

import com.jm.inventory.domain.common.ConflictException;
import java.util.UUID;

/**
 * Reused for every action that requires {@code OPEN} (recording a count, approving,
 * cancelling) - there is only one precondition to violate, see
 * docs/specs/09-cycle-counts/requirements.md's "Decision".
 */
public class CycleCountNotOpenException extends ConflictException {

    public CycleCountNotOpenException(UUID cycleCountId) {
        super("Cycle count " + cycleCountId + " is not OPEN");
    }

    @Override
    public String code() {
        return "CYCLE_COUNT_NOT_OPEN";
    }
}
