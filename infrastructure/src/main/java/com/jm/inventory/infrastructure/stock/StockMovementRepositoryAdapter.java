package com.jm.inventory.infrastructure.stock;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.stock.StockMovement;
import com.jm.inventory.domain.stock.StockMovementFilter;
import com.jm.inventory.domain.stock.StockMovementRepository;
import com.jm.inventory.infrastructure.stock.persistence.StockMovementJpaEntity;
import com.jm.inventory.infrastructure.stock.persistence.StockMovementSpecifications;
import com.jm.inventory.infrastructure.stock.persistence.StockMovementSpringDataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Append-only: {@link #save} is always a fresh insert - a {@link StockMovement} is never
 * loaded and mutated the way {@code Category}/{@code Product}/{@code Warehouse} are, since
 * the ledger has no update path at all.
 */
@Component
public class StockMovementRepositoryAdapter implements StockMovementRepository {

    private final StockMovementSpringDataRepository springDataRepository;

    public StockMovementRepositoryAdapter(StockMovementSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public StockMovement save(StockMovement movement) {
        StockMovementJpaEntity entity = StockMovementJpaEntity.builder()
                .id(movement.getId())
                .productId(movement.getProductId())
                .warehouseId(movement.getWarehouseId())
                .type(movement.getType())
                .quantity(movement.getQuantity())
                .occurredAt(movement.getOccurredAt())
                .reference(movement.getReference())
                .relatedMovementId(movement.getRelatedMovementId())
                .build();
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<StockMovement> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PagedResult<StockMovement> findAll(StockMovementFilter filter, int page, int pageSize) {
        Specification<StockMovementJpaEntity> specification = StockMovementSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("occurredAt").descending());
        Page<StockMovementJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    @Override
    public List<StockMovement> findAllMatching(StockMovementFilter filter) {
        Specification<StockMovementJpaEntity> specification = StockMovementSpecifications.matching(filter);
        return springDataRepository.findAll(specification).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Instant> findMostRecentOccurredAt(UUID productId, UUID warehouseId) {
        return Optional.ofNullable(springDataRepository.findMostRecentOccurredAt(productId, warehouseId));
    }

    private StockMovement toDomain(StockMovementJpaEntity entity) {
        return new StockMovement(
                entity.getId(),
                entity.getProductId(),
                entity.getWarehouseId(),
                entity.getType(),
                entity.getQuantity(),
                entity.getOccurredAt(),
                entity.getReference(),
                entity.getRelatedMovementId());
    }
}
