package com.jm.inventory.infrastructure.purchasing;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.purchasing.PurchaseOrder;
import com.jm.inventory.domain.purchasing.PurchaseOrderFilter;
import com.jm.inventory.domain.purchasing.PurchaseOrderLine;
import com.jm.inventory.domain.purchasing.PurchaseOrderRepository;
import com.jm.inventory.infrastructure.purchasing.persistence.PurchaseOrderJpaEntity;
import com.jm.inventory.infrastructure.purchasing.persistence.PurchaseOrderLineJpaEntity;
import com.jm.inventory.infrastructure.purchasing.persistence.PurchaseOrderLineSpringDataRepository;
import com.jm.inventory.infrastructure.purchasing.persistence.PurchaseOrderSpecifications;
import com.jm.inventory.infrastructure.purchasing.persistence.PurchaseOrderSpringDataRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Composes the {@link PurchaseOrder} aggregate (order + lines) by hand from two separate
 * tables, instead of a JPA {@code @OneToMany} - lines have a plain {@code purchaseOrderId}
 * column, queried separately and assembled here, same adapter-composes-the-aggregate
 * discipline every other feature in this project follows.
 */
@Component
public class PurchaseOrderRepositoryAdapter implements PurchaseOrderRepository {

    private final PurchaseOrderSpringDataRepository springDataRepository;
    private final PurchaseOrderLineSpringDataRepository lineSpringDataRepository;

    public PurchaseOrderRepositoryAdapter(
            PurchaseOrderSpringDataRepository springDataRepository,
            PurchaseOrderLineSpringDataRepository lineSpringDataRepository) {
        this.springDataRepository = springDataRepository;
        this.lineSpringDataRepository = lineSpringDataRepository;
    }

    @Override
    public PurchaseOrder save(PurchaseOrder purchaseOrder) {
        PurchaseOrderJpaEntity entity = springDataRepository.findById(purchaseOrder.getId())
                .map(existing -> applyTo(existing, purchaseOrder))
                .orElseGet(() -> toNewEntity(purchaseOrder));
        PurchaseOrderJpaEntity saved = springDataRepository.save(entity);
        saveLines(purchaseOrder);
        return toDomain(saved, findLineEntities(purchaseOrder.getId()));
    }

    @Override
    public Optional<PurchaseOrder> findById(UUID id) {
        return springDataRepository.findById(id).map(entity -> toDomain(entity, findLineEntities(id)));
    }

    @Override
    public PagedResult<PurchaseOrder> findAll(PurchaseOrderFilter filter, int page, int pageSize) {
        Specification<PurchaseOrderJpaEntity> specification = PurchaseOrderSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<PurchaseOrderJpaEntity> result = springDataRepository.findAll(specification, pageable);
        List<PurchaseOrder> items = result.getContent().stream()
                .map(entity -> toDomain(entity, findLineEntities(entity.getId())))
                .toList();
        return new PagedResult<>(items, result.getTotalElements());
    }

    private void saveLines(PurchaseOrder purchaseOrder) {
        for (PurchaseOrderLine line : purchaseOrder.getLines()) {
            PurchaseOrderLineJpaEntity lineEntity = lineSpringDataRepository.findById(line.getId())
                    .map(existing -> {
                        existing.setQuantityReceived(line.getQuantityReceived());
                        return existing;
                    })
                    .orElseGet(() -> PurchaseOrderLineJpaEntity.builder()
                            .id(line.getId())
                            .purchaseOrderId(purchaseOrder.getId())
                            .productId(line.getProductId())
                            .quantityOrdered(line.getQuantityOrdered())
                            .quantityReceived(line.getQuantityReceived())
                            .unitCost(line.getUnitCost())
                            .build());
            lineSpringDataRepository.save(lineEntity);
        }
    }

    private List<PurchaseOrderLineJpaEntity> findLineEntities(UUID purchaseOrderId) {
        return lineSpringDataRepository.findByPurchaseOrderId(purchaseOrderId);
    }

    private PurchaseOrderJpaEntity applyTo(PurchaseOrderJpaEntity entity, PurchaseOrder purchaseOrder) {
        entity.setStatus(purchaseOrder.getStatus());
        entity.setExpectedDeliveryDate(purchaseOrder.getExpectedDeliveryDate());
        return entity;
    }

    private PurchaseOrderJpaEntity toNewEntity(PurchaseOrder purchaseOrder) {
        return PurchaseOrderJpaEntity.builder()
                .id(purchaseOrder.getId())
                .supplierId(purchaseOrder.getSupplierId())
                .warehouseId(purchaseOrder.getWarehouseId())
                .status(purchaseOrder.getStatus())
                .expectedDeliveryDate(purchaseOrder.getExpectedDeliveryDate())
                .build();
    }

    private PurchaseOrder toDomain(PurchaseOrderJpaEntity entity, List<PurchaseOrderLineJpaEntity> lineEntities) {
        List<PurchaseOrderLine> lines = lineEntities.stream().map(this::toDomainLine).toList();
        return new PurchaseOrder(
                entity.getId(), entity.getSupplierId(), entity.getWarehouseId(), entity.getStatus(),
                entity.getExpectedDeliveryDate(), lines);
    }

    private PurchaseOrderLine toDomainLine(PurchaseOrderLineJpaEntity entity) {
        return new PurchaseOrderLine(
                entity.getId(), entity.getProductId(), entity.getQuantityOrdered(), entity.getQuantityReceived(),
                entity.getUnitCost());
    }
}
