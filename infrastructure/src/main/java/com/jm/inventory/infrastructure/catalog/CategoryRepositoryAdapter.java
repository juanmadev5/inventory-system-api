package com.jm.inventory.infrastructure.catalog;

import com.jm.inventory.domain.catalog.Category;
import com.jm.inventory.domain.catalog.CategoryFilter;
import com.jm.inventory.domain.catalog.CategoryRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.infrastructure.catalog.persistence.CategoryJpaEntity;
import com.jm.inventory.infrastructure.catalog.persistence.CategorySpecifications;
import com.jm.inventory.infrastructure.catalog.persistence.CategorySpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategorySpringDataRepository springDataRepository;

    public CategoryRepositoryAdapter(CategorySpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity entity = springDataRepository.findById(category.getId())
                .map(existing -> applyTo(existing, category))
                .orElseGet(() -> toNewEntity(category));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Category> findByName(String name) {
        return springDataRepository.findByName(name).map(this::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return springDataRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, UUID id) {
        return springDataRepository.existsByNameAndIdNot(name, id);
    }

    @Override
    public PagedResult<Category> findAll(CategoryFilter filter, int page, int pageSize) {
        Specification<CategoryJpaEntity> specification = CategorySpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("name"));
        Page<CategoryJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private CategoryJpaEntity applyTo(CategoryJpaEntity entity, Category category) {
        entity.setName(category.getName());
        entity.setParentCategoryId(category.getParentCategoryId());
        return entity;
    }

    private CategoryJpaEntity toNewEntity(Category category) {
        return CategoryJpaEntity.builder()
                .id(category.getId())
                .name(category.getName())
                .parentCategoryId(category.getParentCategoryId())
                .build();
    }

    private Category toDomain(CategoryJpaEntity entity) {
        return new Category(entity.getId(), entity.getName(), entity.getParentCategoryId());
    }
}
