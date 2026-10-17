package com.jm.inventory.application.catalog;

import com.jm.inventory.domain.catalog.Category;
import com.jm.inventory.domain.catalog.CategoryRepository;
import com.jm.inventory.domain.catalog.UnitOfMeasure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row's worth of DB work (category lookup + product creation), as its own
 * {@code @Transactional} boundary - so one bad row's failure rolls back only that row, never
 * every row already committed before it. Deliberately a separate bean from
 * {@link ProductCsvImportService}: a private method call never goes through the Spring AOP
 * proxy that makes {@code @Transactional} take effect, so this has to be a distinct,
 * injected collaborator, not a private method on the importer itself.
 */
@Service
public class ProductImportRowService {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;

    public ProductImportRowService(ProductService productService, CategoryRepository categoryRepository) {
        this.productService = productService;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void importRow(String sku, String name, String description, String categoryName, UnitOfMeasure unitOfMeasure) {
        Category category = categoryRepository.findByName(categoryName)
                .orElseThrow(() -> new ProductImportRowException("category '" + categoryName + "' does not exist"));
        productService.create(sku, name, description, category.getId(), unitOfMeasure);
    }
}
