package com.jm.inventory.domain.catalog;

import java.util.UUID;

/**
 * Every field is optional - {@code null} means "no filter on this field".
 */
public record CategoryFilter(String name, UUID parentCategoryId) {
}
