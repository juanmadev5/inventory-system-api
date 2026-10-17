package com.jm.inventory.application.catalog;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.catalog.Category;
import com.jm.inventory.domain.catalog.CategoryFilter;
import com.jm.inventory.domain.catalog.CategoryNameAlreadyExistsException;
import com.jm.inventory.domain.catalog.CategoryNotFoundException;
import com.jm.inventory.domain.catalog.CategoryRepository;
import com.jm.inventory.domain.catalog.UnknownParentCategoryException;
import com.jm.inventory.domain.common.PagedResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    public CategoryService(CategoryRepository categoryRepository, AuditLogService auditLogService) {
        this.categoryRepository = categoryRepository;
        this.auditLogService = auditLogService;
    }

    public Category create(String name, UUID parentCategoryId) {
        if (categoryRepository.existsByName(name)) {
            throw new CategoryNameAlreadyExistsException(name);
        }
        requireExistingParent(parentCategoryId);
        Category created = categoryRepository.save(new Category(UUID.randomUUID(), name, parentCategoryId));
        auditLogService.recordCreated(AuditEntityType.CATEGORY, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public Category get(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }

    public Category update(UUID id, String name, UUID parentCategoryId) {
        Category existing = get(id);
        String newName = name != null ? name : existing.getName();
        UUID newParentCategoryId = parentCategoryId != null ? parentCategoryId : existing.getParentCategoryId();

        if (name != null && categoryRepository.existsByNameAndIdNot(name, id)) {
            throw new CategoryNameAlreadyExistsException(name);
        }
        if (parentCategoryId != null) {
            requireExistingParent(parentCategoryId);
        }
        Category updated = categoryRepository.save(new Category(id, newName, newParentCategoryId));
        auditLogService.recordUpdated(AuditEntityType.CATEGORY, id, existing, updated);
        return updated;
    }

    @Transactional(readOnly = true)
    public PagedResult<Category> list(CategoryFilter filter, Integer page, Integer pageSize) {
        return categoryRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    private void requireExistingParent(UUID parentCategoryId) {
        if (parentCategoryId != null && categoryRepository.findById(parentCategoryId).isEmpty()) {
            throw new UnknownParentCategoryException(parentCategoryId);
        }
    }
}
