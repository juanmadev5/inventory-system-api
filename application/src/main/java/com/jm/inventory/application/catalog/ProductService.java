package com.jm.inventory.application.catalog;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.catalog.CategoryRepository;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.ProductFilter;
import com.jm.inventory.domain.catalog.ProductNotFoundException;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.catalog.ProductSkuAlreadyExistsException;
import com.jm.inventory.domain.catalog.UnitOfMeasure;
import com.jm.inventory.domain.catalog.UnknownCategoryReferenceException;
import com.jm.inventory.domain.common.PagedResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    public ProductService(
            ProductRepository productRepository, CategoryRepository categoryRepository, AuditLogService auditLogService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.auditLogService = auditLogService;
    }

    public Product create(String sku, String name, String description, UUID categoryId, UnitOfMeasure unitOfMeasure) {
        if (productRepository.existsBySku(sku)) {
            throw new ProductSkuAlreadyExistsException(sku);
        }
        requireExistingCategory(categoryId);
        Product created = productRepository.save(
                new Product(UUID.randomUUID(), sku, name, description, categoryId, unitOfMeasure, true));
        auditLogService.recordCreated(AuditEntityType.PRODUCT, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public Product get(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product update(UUID id, String name, String description, UUID categoryId, Boolean active) {
        Product existing = get(id);
        if (categoryId != null) {
            requireExistingCategory(categoryId);
        }
        Product updated = new Product(
                existing.getId(),
                existing.getSku(),
                name != null ? name : existing.getName(),
                description != null ? description : existing.getDescription(),
                categoryId != null ? categoryId : existing.getCategoryId(),
                existing.getUnitOfMeasure(),
                active != null ? active : existing.isActive());
        Product saved = productRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.PRODUCT, id, existing, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PagedResult<Product> list(ProductFilter filter, Integer page, Integer pageSize) {
        return productRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    private void requireExistingCategory(UUID categoryId) {
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw new UnknownCategoryReferenceException(categoryId);
        }
    }
}
