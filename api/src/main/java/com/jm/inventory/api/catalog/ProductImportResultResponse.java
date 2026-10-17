package com.jm.inventory.api.catalog;

import com.jm.inventory.application.catalog.ProductImportResult;
import java.util.List;

public record ProductImportResultResponse(int totalRows, int importedCount, List<ProductImportRowErrorResponse> errors) {

    public static ProductImportResultResponse from(ProductImportResult result) {
        return new ProductImportResultResponse(
                result.totalRows(),
                result.importedCount(),
                result.errors().stream().map(ProductImportRowErrorResponse::from).toList());
    }
}
