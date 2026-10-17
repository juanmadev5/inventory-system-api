package com.jm.inventory.application.catalog;

/**
 * {@code row} is 1-based over data rows only - the header is not row 1. {@code sku} is the
 * row's raw CSV value even when the failure is unrelated to the SKU, so the caller can
 * locate the row in their source file.
 */
public record ProductImportRowError(int row, String sku, String message) {
}
