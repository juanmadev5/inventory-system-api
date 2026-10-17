package com.jm.inventory.application.catalog;

/**
 * Shared between {@link ProductCsvImportService} and {@link ProductCsvExportService} so
 * export's output always re-imports unchanged - see
 * docs/specs/12-catalog-bulk-import-export/requirements.md's round-trip requirement.
 */
public final class ProductCsvColumns {

    public static final String SKU = "sku";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String CATEGORY_NAME = "categoryName";
    public static final String UNIT_OF_MEASURE = "unitOfMeasure";

    public static final String[] HEADER = {SKU, NAME, DESCRIPTION, CATEGORY_NAME, UNIT_OF_MEASURE};

    private ProductCsvColumns() {
    }
}
