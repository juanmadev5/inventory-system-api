package com.jm.inventory.domain.stock;

import java.util.UUID;

public record StockLevelFilter(UUID productId, UUID warehouseId) {
}
