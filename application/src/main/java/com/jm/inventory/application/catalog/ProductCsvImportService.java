package com.jm.inventory.application.catalog;

import com.jm.inventory.domain.catalog.InvalidProductImportFileException;
import com.jm.inventory.domain.catalog.ProductSkuAlreadyExistsException;
import com.jm.inventory.domain.catalog.UnitOfMeasure;
import com.jm.inventory.domain.catalog.UnknownCategoryReferenceException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

/**
 * Row-partial-success: every data row is validated and persisted independently - one bad
 * row never rolls back another. Parsing/field-level validation happens here (no DB access,
 * so no transaction needed); the actual category lookup and product creation is delegated to
 * {@link ProductImportRowService}, a separate bean so each row's DB work runs inside its own
 * {@code @Transactional} boundary. See docs/specs/12-catalog-bulk-import-export/requirements.md.
 */
@Service
public class ProductCsvImportService {

    private final ProductImportRowService productImportRowService;

    public ProductCsvImportService(ProductImportRowService productImportRowService) {
        this.productImportRowService = productImportRowService;
    }

    public ProductImportResult importFrom(Reader csvReader) {
        List<CSVRecord> dataRows = parse(csvReader);
        if (dataRows.size() > InvalidProductImportFileException.MAX_ROWS) {
            throw InvalidProductImportFileException.rowLimitExceeded(dataRows.size());
        }

        List<ProductImportRowError> errors = new ArrayList<>();
        int importedCount = 0;
        for (int rowIndex = 0; rowIndex < dataRows.size(); rowIndex++) {
            CSVRecord dataRow = dataRows.get(rowIndex);
            String sku = dataRow.get(ProductCsvColumns.SKU).trim();
            try {
                importRow(dataRow, sku);
                importedCount++;
            } catch (ProductImportRowException | ProductSkuAlreadyExistsException
                    | UnknownCategoryReferenceException invalidRow) {
                errors.add(new ProductImportRowError(rowIndex + 1, sku, invalidRow.getMessage()));
            }
        }
        return new ProductImportResult(dataRows.size(), importedCount, errors);
    }

    private void importRow(CSVRecord dataRow, String sku) {
        String name = dataRow.get(ProductCsvColumns.NAME).trim();
        String description = dataRow.get(ProductCsvColumns.DESCRIPTION).trim();
        String categoryName = dataRow.get(ProductCsvColumns.CATEGORY_NAME).trim();
        String unitOfMeasureName = dataRow.get(ProductCsvColumns.UNIT_OF_MEASURE).trim();

        if (sku.isBlank()) {
            throw new ProductImportRowException("sku is required");
        }
        if (name.isBlank()) {
            throw new ProductImportRowException("name is required");
        }
        if (categoryName.isBlank()) {
            throw new ProductImportRowException("categoryName is required");
        }
        UnitOfMeasure unitOfMeasure = parseUnitOfMeasure(unitOfMeasureName);

        productImportRowService.importRow(sku, name, description, categoryName, unitOfMeasure);
    }

    private UnitOfMeasure parseUnitOfMeasure(String rawValue) {
        try {
            return UnitOfMeasure.valueOf(rawValue);
        } catch (IllegalArgumentException notARecognizedUnit) {
            throw new ProductImportRowException("unitOfMeasure must be one of "
                    + Arrays.toString(UnitOfMeasure.values()) + ", got '" + rawValue + "'");
        }
    }

    private List<CSVRecord> parse(Reader csvReader) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .get();
        try (CSVParser parser = format.parse(csvReader)) {
            requireAllColumnsPresent(parser);
            return parser.getRecords();
        } catch (IOException malformedCsv) {
            throw new UncheckedIOException(malformedCsv);
        }
    }

    private void requireAllColumnsPresent(CSVParser parser) {
        Set<String> missingColumns = new LinkedHashSet<>();
        for (String requiredColumn : ProductCsvColumns.HEADER) {
            if (!parser.getHeaderNames().contains(requiredColumn)) {
                missingColumns.add(requiredColumn);
            }
        }
        if (!missingColumns.isEmpty()) {
            throw InvalidProductImportFileException.missingColumns(missingColumns);
        }
    }
}
