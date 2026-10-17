package com.jm.inventory.application.catalog;

import java.util.List;

public record ProductImportResult(int totalRows, int importedCount, List<ProductImportRowError> errors) {
}
