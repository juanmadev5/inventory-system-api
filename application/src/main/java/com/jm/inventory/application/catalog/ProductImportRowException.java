package com.jm.inventory.application.catalog;

/**
 * Signals that a single CSV row's data is invalid - caught in
 * {@link ProductCsvImportService#importFrom}'s loop and turned into a
 * {@link ProductImportRowError}, never surfaced as an HTTP error on its own.
 */
public class ProductImportRowException extends RuntimeException {

    public ProductImportRowException(String message) {
        super(message);
    }
}
