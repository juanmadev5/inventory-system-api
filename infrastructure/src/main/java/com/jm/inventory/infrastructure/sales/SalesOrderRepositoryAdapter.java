package com.jm.inventory.infrastructure.sales;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.sales.SalesOrder;
import com.jm.inventory.domain.sales.SalesOrderFilter;
import com.jm.inventory.domain.sales.SalesOrderLine;
import com.jm.inventory.domain.sales.SalesOrderRepository;
import com.jm.inventory.infrastructure.sales.persistence.SalesOrderJpaEntity;
import com.jm.inventory.infrastructure.sales.persistence.SalesOrderLineJpaEntity;
import com.jm.inventory.infrastructure.sales.persistence.SalesOrderLineSpringDataRepository;
import com.jm.inventory.infrastructure.sales.persistence.SalesOrderSpecifications;
import com.jm.inventory.infrastructure.sales.persistence.SalesOrderSpringDataRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Composes the {@link SalesOrder} aggregate (order + lines) by hand from two separate
 * tables, same discipline as {@code PurchaseOrderRepositoryAdapter}.
 */
@Component
public class SalesOrderRepositoryAdapter implements SalesOrderRepository {

    private final SalesOrderSpringDataRepository springDataRepository;
    private final SalesOrderLineSpringDataRepository lineSpringDataRepository;

    public SalesOrderRepositoryAdapter(
            SalesOrderSpringDataRepository springDataRepository,
            SalesOrderLineSpringDataRepository lineSpringDataRepository) {
        this.springDataRepository = springDataRepository;
        this.lineSpringDataRepository = lineSpringDataRepository;
    }

    @Override
    public SalesOrder save(SalesOrder salesOrder) {
        SalesOrderJpaEntity entity = springDataRepository.findById(salesOrder.getId())
                .map(existing -> applyTo(existing, salesOrder))
                .orElseGet(() -> toNewEntity(salesOrder));
        SalesOrderJpaEntity saved = springDataRepository.save(entity);
        saveLines(salesOrder);
        return toDomain(saved, findLineEntities(salesOrder.getId()));
    }

    @Override
    public Optional<SalesOrder> findById(UUID id) {
        return springDataRepository.findById(id).map(entity -> toDomain(entity, findLineEntities(id)));
    }

    @Override
    public PagedResult<SalesOrder> findAll(SalesOrderFilter filter, int page, int pageSize) {
        Specification<SalesOrderJpaEntity> specification = SalesOrderSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<SalesOrderJpaEntity> result = springDataRepository.findAll(specification, pageable);
        List<SalesOrder> items = result.getContent().stream()
                .map(entity -> toDomain(entity, findLineEntities(entity.getId())))
                .toList();
        return new PagedResult<>(items, result.getTotalElements());
    }

    private void saveLines(SalesOrder salesOrder) {
        for (SalesOrderLine line : salesOrder.getLines()) {
            SalesOrderLineJpaEntity lineEntity = lineSpringDataRepository.findById(line.getId())
                    .map(existing -> {
                        existing.setQuantityShipped(line.getQuantityShipped());
                        return existing;
                    })
                    .orElseGet(() -> SalesOrderLineJpaEntity.builder()
                            .id(line.getId())
                            .salesOrderId(salesOrder.getId())
                            .productId(line.getProductId())
                            .quantityOrdered(line.getQuantityOrdered())
                            .quantityShipped(line.getQuantityShipped())
                            .unitPrice(line.getUnitPrice())
                            .build());
            lineSpringDataRepository.save(lineEntity);
        }
    }

    private List<SalesOrderLineJpaEntity> findLineEntities(UUID salesOrderId) {
        return lineSpringDataRepository.findBySalesOrderId(salesOrderId);
    }

    private SalesOrderJpaEntity applyTo(SalesOrderJpaEntity entity, SalesOrder salesOrder) {
        entity.setStatus(salesOrder.getStatus());
        entity.setExpectedShipDate(salesOrder.getExpectedShipDate());
        return entity;
    }

    private SalesOrderJpaEntity toNewEntity(SalesOrder salesOrder) {
        return SalesOrderJpaEntity.builder()
                .id(salesOrder.getId())
                .customerId(salesOrder.getCustomerId())
                .warehouseId(salesOrder.getWarehouseId())
                .status(salesOrder.getStatus())
                .expectedShipDate(salesOrder.getExpectedShipDate())
                .build();
    }

    private SalesOrder toDomain(SalesOrderJpaEntity entity, List<SalesOrderLineJpaEntity> lineEntities) {
        List<SalesOrderLine> lines = lineEntities.stream().map(this::toDomainLine).toList();
        return new SalesOrder(
                entity.getId(), entity.getCustomerId(), entity.getWarehouseId(), entity.getStatus(),
                entity.getExpectedShipDate(), lines);
    }

    private SalesOrderLine toDomainLine(SalesOrderLineJpaEntity entity) {
        return new SalesOrderLine(
                entity.getId(), entity.getProductId(), entity.getQuantityOrdered(), entity.getQuantityShipped(),
                entity.getUnitPrice());
    }
}
