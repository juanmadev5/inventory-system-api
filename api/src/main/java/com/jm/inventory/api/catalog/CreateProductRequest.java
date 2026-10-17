package com.jm.inventory.api.catalog;

import com.jm.inventory.domain.catalog.UnitOfMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotNull UUID categoryId,
        @NotNull UnitOfMeasure unitOfMeasure) {
}
