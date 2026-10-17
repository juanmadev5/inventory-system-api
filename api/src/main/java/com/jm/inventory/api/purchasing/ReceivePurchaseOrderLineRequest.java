package com.jm.inventory.api.purchasing;

import jakarta.validation.constraints.Min;

public record ReceivePurchaseOrderLineRequest(@Min(1) int quantity) {
}
