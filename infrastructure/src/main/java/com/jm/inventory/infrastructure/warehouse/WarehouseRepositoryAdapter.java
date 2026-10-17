package com.jm.inventory.infrastructure.warehouse;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.warehouse.Warehouse;
import com.jm.inventory.domain.warehouse.WarehouseFilter;
import com.jm.inventory.domain.warehouse.WarehouseRepository;
import com.jm.inventory.infrastructure.warehouse.persistence.WarehouseJpaEntity;
import com.jm.inventory.infrastructure.warehouse.persistence.WarehouseSpecifications;
import com.jm.inventory.infrastructure.warehouse.persistence.WarehouseSpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class WarehouseRepositoryAdapter implements WarehouseRepository {

    private final WarehouseSpringDataRepository springDataRepository;

    public WarehouseRepositoryAdapter(WarehouseSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        WarehouseJpaEntity entity = springDataRepository.findById(warehouse.getId())
                .map(existing -> applyTo(existing, warehouse))
                .orElseGet(() -> toNewEntity(warehouse));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Warehouse> findById(UUID id) {
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
    public PagedResult<Warehouse> findAll(WarehouseFilter filter, int page, int pageSize) {
        Specification<WarehouseJpaEntity> specification = WarehouseSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("name"));
        Page<WarehouseJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private WarehouseJpaEntity applyTo(WarehouseJpaEntity entity, Warehouse warehouse) {
        entity.setName(warehouse.getName());
        entity.setAddress(warehouse.getAddress());
        entity.setActive(warehouse.isActive());
        return entity;
    }

    private WarehouseJpaEntity toNewEntity(Warehouse warehouse) {
        return WarehouseJpaEntity.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .address(warehouse.getAddress())
                .active(warehouse.isActive())
                .build();
    }

    private Warehouse toDomain(WarehouseJpaEntity entity) {
        return new Warehouse(entity.getId(), entity.getName(), entity.getAddress(), entity.isActive());
    }
}
