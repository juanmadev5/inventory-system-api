package com.jm.inventory.api.purchasing;

import java.time.LocalDate;

public record UpdatePurchaseOrderRequest(LocalDate expectedDeliveryDate) {
}
