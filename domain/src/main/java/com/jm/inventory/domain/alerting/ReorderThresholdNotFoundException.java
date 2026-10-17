package com.jm.inventory.domain.alerting;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class ReorderThresholdNotFoundException extends NotFoundException {

    public ReorderThresholdNotFoundException(UUID id) {
        super("Reorder threshold " + id + " not found");
    }

    @Override
    public String code() {
        return "REORDER_THRESHOLD_NOT_FOUND";
    }
}
