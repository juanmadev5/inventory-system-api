package com.jm.inventory.infrastructure.alerting;

import com.jm.inventory.domain.alerting.ReorderThreshold;
import com.jm.inventory.domain.alerting.ReorderThresholdFilter;
import com.jm.inventory.domain.alerting.ReorderThresholdRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.infrastructure.alerting.persistence.ReorderThresholdJpaEntity;
import com.jm.inventory.infrastructure.alerting.persistence.ReorderThresholdSpecifications;
import com.jm.inventory.infrastructure.alerting.persistence.ReorderThresholdSpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class ReorderThresholdRepositoryAdapter implements ReorderThresholdRepository {

    private final ReorderThresholdSpringDataRepository springDataRepository;

    public ReorderThresholdRepositoryAdapter(ReorderThresholdSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public ReorderThreshold save(ReorderThreshold reorderThreshold) {
        ReorderThresholdJpaEntity entity = springDataRepository.findById(reorderThreshold.getId())
                .map(existing -> applyTo(existing, reorderThreshold))
                .orElseGet(() -> toNewEntity(reorderThreshold));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<ReorderThreshold> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ReorderThreshold> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId) {
        return springDataRepository.findByProductIdAndWarehouseId(productId, warehouseId).map(this::toDomain);
    }

    @Override
    public PagedResult<ReorderThreshold> findAll(ReorderThresholdFilter filter, int page, int pageSize) {
        Specification<ReorderThresholdJpaEntity> specification = ReorderThresholdSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<ReorderThresholdJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private ReorderThresholdJpaEntity applyTo(ReorderThresholdJpaEntity entity, ReorderThreshold reorderThreshold) {
        entity.setReorderPoint(reorderThreshold.getReorderPoint());
        entity.setReorderQuantity(reorderThreshold.getReorderQuantity());
        return entity;
    }

    private ReorderThresholdJpaEntity toNewEntity(ReorderThreshold reorderThreshold) {
        return ReorderThresholdJpaEntity.builder()
                .id(reorderThreshold.getId())
                .productId(reorderThreshold.getProductId())
                .warehouseId(reorderThreshold.getWarehouseId())
                .reorderPoint(reorderThreshold.getReorderPoint())
                .reorderQuantity(reorderThreshold.getReorderQuantity())
                .build();
    }

    private ReorderThreshold toDomain(ReorderThresholdJpaEntity entity) {
        return new ReorderThreshold(
                entity.getId(), entity.getProductId(), entity.getWarehouseId(), entity.getReorderPoint(),
                entity.getReorderQuantity());
    }
}
