package com.jm.inventory.api.catalog;

import com.jm.inventory.application.catalog.ProductImportRowError;

public record ProductImportRowErrorResponse(int row, String sku, String message) {

    public static ProductImportRowErrorResponse from(ProductImportRowError error) {
        return new ProductImportRowErrorResponse(error.row(), error.sku(), error.message());
    }
}
