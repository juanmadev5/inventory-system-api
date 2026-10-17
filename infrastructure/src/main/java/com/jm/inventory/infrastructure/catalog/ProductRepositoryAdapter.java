package com.jm.inventory.infrastructure.catalog;

import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.ProductFilter;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.infrastructure.catalog.persistence.ProductJpaEntity;
import com.jm.inventory.infrastructure.catalog.persistence.ProductSpecifications;
import com.jm.inventory.infrastructure.catalog.persistence.ProductSpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductSpringDataRepository springDataRepository;

    public ProductRepositoryAdapter(ProductSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = springDataRepository.findById(product.getId())
                .map(existing -> applyTo(existing, product))
                .orElseGet(() -> toNewEntity(product));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsBySku(String sku) {
        return springDataRepository.existsBySku(sku);
    }

    @Override
    public boolean existsBySkuAndIdNot(String sku, UUID id) {
        return springDataRepository.existsBySkuAndIdNot(sku, id);
    }

    @Override
    public PagedResult<Product> findAll(ProductFilter filter, int page, int pageSize) {
        Specification<ProductJpaEntity> specification = ProductSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("name"));
        Page<ProductJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private ProductJpaEntity applyTo(ProductJpaEntity entity, Product product) {
        entity.setName(product.getName());
        entity.setDescription(product.getDescription());
        entity.setCategoryId(product.getCategoryId());
        entity.setActive(product.isActive());
        return entity;
    }

    private ProductJpaEntity toNewEntity(Product product) {
        return ProductJpaEntity.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategoryId())
                .unitOfMeasure(product.getUnitOfMeasure())
                .active(product.isActive())
                .build();
    }

    private Product toDomain(ProductJpaEntity entity) {
        return new Product(
                entity.getId(),
                entity.getSku(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategoryId(),
                entity.getUnitOfMeasure(),
                entity.isActive());
    }
}
