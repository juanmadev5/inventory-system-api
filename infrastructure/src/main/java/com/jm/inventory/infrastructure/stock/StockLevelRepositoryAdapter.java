package com.jm.inventory.infrastructure.stock;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.stock.StockLevel;
import com.jm.inventory.domain.stock.StockLevelFilter;
import com.jm.inventory.domain.stock.StockLevelRepository;
import com.jm.inventory.infrastructure.stock.persistence.StockLevelJpaEntity;
import com.jm.inventory.infrastructure.stock.persistence.StockLevelSpecifications;
import com.jm.inventory.infrastructure.stock.persistence.StockLevelSpringDataRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class StockLevelRepositoryAdapter implements StockLevelRepository {

    private final StockLevelSpringDataRepository springDataRepository;

    public StockLevelRepositoryAdapter(StockLevelSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<StockLevel> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PagedResult<StockLevel> findAll(StockLevelFilter filter, int page, int pageSize) {
        Specification<StockLevelJpaEntity> specification = StockLevelSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<StockLevelJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    @Override
    public StockLevel lockOrCreateForUpdate(UUID productId, UUID warehouseId) {
        springDataRepository.insertZeroRowIfAbsent(UUID.randomUUID(), productId, warehouseId);
        StockLevelJpaEntity entity = springDataRepository
                .findByProductIdAndWarehouseIdForUpdate(productId, warehouseId)
                .orElseThrow(() -> new IllegalStateException(
                        "Stock level row for product " + productId + " / warehouse " + warehouseId
                                + " must exist after insertZeroRowIfAbsent"));
        return toDomain(entity);
    }

    @Override
    public List<UUID> findProductIdsByWarehouseId(UUID warehouseId) {
        return springDataRepository.findByWarehouseId(warehouseId).stream()
                .map(StockLevelJpaEntity::getProductId)
                .toList();
    }

    @Override
    public StockLevel save(StockLevel stockLevel) {
        StockLevelJpaEntity entity = springDataRepository.findById(stockLevel.getId())
                .orElseThrow(() -> new IllegalStateException("Stock level " + stockLevel.getId() + " not found"));
        entity.setQuantityOnHand(stockLevel.getQuantityOnHand());
        entity.setQuantityReserved(stockLevel.getQuantityReserved());
        return toDomain(springDataRepository.save(entity));
    }

    private StockLevel toDomain(StockLevelJpaEntity entity) {
        return new StockLevel(
                entity.getId(),
                entity.getProductId(),
                entity.getWarehouseId(),
                entity.getQuantityOnHand(),
                entity.getQuantityReserved());
    }
}
