package com.jm.inventory.api.catalog;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateProductRequest(
        @Size(max = 200) String name,
        String description,
        UUID categoryId,
        Boolean active) {
}
