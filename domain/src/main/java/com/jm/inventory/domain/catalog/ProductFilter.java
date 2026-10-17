package com.jm.inventory.domain.catalog;

import java.util.UUID;

/**
 * {@code active}: {@code null} means no filter (both active and inactive match) -
 * resolving "not specified defaults to active-only" is the API layer's job (see
 * docs/specs/01-catalog-and-stock/requirements.md), not this filter's.
 */
public record ProductFilter(UUID categoryId, Boolean active, String sku) {
}
