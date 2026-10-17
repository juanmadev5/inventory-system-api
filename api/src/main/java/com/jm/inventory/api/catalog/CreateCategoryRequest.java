package com.jm.inventory.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 200) String name,
        UUID parentCategoryId) {
}
