package com.jm.inventory.api.alerting;

import jakarta.validation.constraints.Min;

public record UpdateReorderThresholdRequest(
        @Min(0) Integer reorderPoint,
        @Min(1) Integer reorderQuantity) {
}
