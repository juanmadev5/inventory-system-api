package com.jm.inventory.api.catalog;

import com.jm.inventory.domain.catalog.Category;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, UUID parentCategoryId) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getParentCategoryId());
    }
}
