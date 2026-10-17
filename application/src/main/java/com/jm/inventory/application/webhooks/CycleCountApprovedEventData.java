package com.jm.inventory.application.webhooks;

import java.util.UUID;

public record CycleCountApprovedEventData(UUID cycleCountId, UUID warehouseId) {
}
