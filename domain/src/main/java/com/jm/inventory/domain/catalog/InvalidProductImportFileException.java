package com.jm.inventory.domain.catalog;

import com.jm.inventory.domain.common.InvalidReferenceException;
import java.util.Set;

/**
 * The uploaded file itself is rejected before any row is processed - as opposed to a single
 * row's data being invalid, which is reported per-row in {@code ProductImportResult} and
 * never thrown. See docs/specs/12-catalog-bulk-import-export/requirements.md.
 */
public class InvalidProductImportFileException extends InvalidReferenceException {

    public static final int MAX_ROWS = 1000;

    private InvalidProductImportFileException(String message) {
        super("file", message);
    }

    public static InvalidProductImportFileException rowLimitExceeded(int actualRowCount) {
        return new InvalidProductImportFileException(
                "A product import file may contain at most " + MAX_ROWS + " data rows, got " + actualRowCount);
    }

    public static InvalidProductImportFileException missingColumns(Set<String> missingColumns) {
        return new InvalidProductImportFileException(
                "The CSV header is missing required column(s): " + String.join(", ", missingColumns));
    }
}
