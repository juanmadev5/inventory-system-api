package com.jm.inventory.api.catalog;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Every field optional - {@code null} means "leave unchanged" (not "clear to null"); this
 * feature has no way to explicitly clear {@code parentCategoryId} back to root-level yet.
 */
public record UpdateCategoryRequest(
        @Size(max = 200) String name,
        UUID parentCategoryId) {
}
