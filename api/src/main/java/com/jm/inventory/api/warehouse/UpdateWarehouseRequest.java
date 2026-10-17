package com.jm.inventory.api.warehouse;

import jakarta.validation.constraints.Size;

public record UpdateWarehouseRequest(
        @Size(max = 200) String name,
        String address,
        Boolean active) {
}
