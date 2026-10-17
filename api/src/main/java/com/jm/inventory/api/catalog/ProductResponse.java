package com.jm.inventory.api.catalog;

import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.UnitOfMeasure;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        UUID categoryId,
        UnitOfMeasure unitOfMeasure,
        boolean active) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getCategoryId(),
                product.getUnitOfMeasure(),
                product.isActive());
    }
}
