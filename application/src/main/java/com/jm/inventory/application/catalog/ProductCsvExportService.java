package com.jm.inventory.application.catalog;

import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.catalog.CategoryNotFoundException;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.ProductFilter;
import com.jm.inventory.domain.common.PagedResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

/**
 * Pages through the repository in fixed-size batches (never loads the whole catalog
 * into memory at once) and writes the exact column order {@link ProductCsvImportService}
 * expects, so an export re-imports unchanged. See
 * docs/specs/12-catalog-bulk-import-export/requirements.md.
 */
@Service
public class ProductCsvExportService {

    private final ProductService productService;
    private final CategoryService categoryService;

    public ProductCsvExportService(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    public void exportTo(Writer csvWriter, ProductFilter filter) {
        Map<UUID, String> categoryNamesById = new HashMap<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(ProductCsvColumns.HEADER).get();
        try (CSVPrinter printer = new CSVPrinter(csvWriter, format)) {
            int page = PaginationDefaults.FIRST_PAGE;
            while (true) {
                PagedResult<Product> batch = productService.list(filter, page, PaginationDefaults.MAX_PAGE_SIZE);
                for (Product product : batch.items()) {
                    printer.printRecord(
                            product.getSku(),
                            product.getName(),
                            product.getDescription(),
                            categoryNameOf(product.getCategoryId(), categoryNamesById),
                            product.getUnitOfMeasure().name());
                }
                if (batch.items().size() < PaginationDefaults.MAX_PAGE_SIZE) {
                    break;
                }
                page++;
            }
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private String categoryNameOf(UUID categoryId, Map<UUID, String> categoryNamesById) {
        return categoryNamesById.computeIfAbsent(categoryId, id -> {
            try {
                return categoryService.get(id).getName();
            } catch (CategoryNotFoundException categoryNoLongerExists) {
                return "";
            }
        });
    }
}
