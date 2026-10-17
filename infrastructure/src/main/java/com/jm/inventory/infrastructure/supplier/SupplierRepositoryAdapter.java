package com.jm.inventory.infrastructure.supplier;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.supplier.Supplier;
import com.jm.inventory.domain.supplier.SupplierFilter;
import com.jm.inventory.domain.supplier.SupplierRepository;
import com.jm.inventory.infrastructure.supplier.persistence.SupplierJpaEntity;
import com.jm.inventory.infrastructure.supplier.persistence.SupplierSpecifications;
import com.jm.inventory.infrastructure.supplier.persistence.SupplierSpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class SupplierRepositoryAdapter implements SupplierRepository {

    private final SupplierSpringDataRepository springDataRepository;

    public SupplierRepositoryAdapter(SupplierSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Supplier save(Supplier supplier) {
        SupplierJpaEntity entity = springDataRepository.findById(supplier.getId())
                .map(existing -> applyTo(existing, supplier))
                .orElseGet(() -> toNewEntity(supplier));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Supplier> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
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
    public PagedResult<Supplier> findAll(SupplierFilter filter, int page, int pageSize) {
        Specification<SupplierJpaEntity> specification = SupplierSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("name"));
        Page<SupplierJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private SupplierJpaEntity applyTo(SupplierJpaEntity entity, Supplier supplier) {
        entity.setName(supplier.getName());
        entity.setEmail(supplier.getEmail());
        entity.setPhone(supplier.getPhone());
        return entity;
    }

    private SupplierJpaEntity toNewEntity(Supplier supplier) {
        return SupplierJpaEntity.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .build();
    }

    private Supplier toDomain(SupplierJpaEntity entity) {
        return new Supplier(entity.getId(), entity.getName(), entity.getEmail(), entity.getPhone());
    }
}
