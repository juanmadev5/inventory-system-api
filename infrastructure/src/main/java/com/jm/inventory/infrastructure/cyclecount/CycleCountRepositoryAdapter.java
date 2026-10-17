package com.jm.inventory.infrastructure.cyclecount;

import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.cyclecount.CycleCount;
import com.jm.inventory.domain.cyclecount.CycleCountFilter;
import com.jm.inventory.domain.cyclecount.CycleCountLine;
import com.jm.inventory.domain.cyclecount.CycleCountRepository;
import com.jm.inventory.infrastructure.cyclecount.persistence.CycleCountJpaEntity;
import com.jm.inventory.infrastructure.cyclecount.persistence.CycleCountLineJpaEntity;
import com.jm.inventory.infrastructure.cyclecount.persistence.CycleCountLineSpringDataRepository;
import com.jm.inventory.infrastructure.cyclecount.persistence.CycleCountSpecifications;
import com.jm.inventory.infrastructure.cyclecount.persistence.CycleCountSpringDataRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Composes the {@link CycleCount} aggregate (session + lines) by hand from two separate
 * tables, same discipline as {@code PurchaseOrderRepositoryAdapter}.
 */
@Component
public class CycleCountRepositoryAdapter implements CycleCountRepository {

    private final CycleCountSpringDataRepository springDataRepository;
    private final CycleCountLineSpringDataRepository lineSpringDataRepository;

    public CycleCountRepositoryAdapter(
            CycleCountSpringDataRepository springDataRepository,
            CycleCountLineSpringDataRepository lineSpringDataRepository) {
        this.springDataRepository = springDataRepository;
        this.lineSpringDataRepository = lineSpringDataRepository;
    }

    @Override
    public CycleCount save(CycleCount cycleCount) {
        CycleCountJpaEntity entity = springDataRepository.findById(cycleCount.getId())
                .map(existing -> applyTo(existing, cycleCount))
                .orElseGet(() -> toNewEntity(cycleCount));
        CycleCountJpaEntity saved = springDataRepository.save(entity);
        saveLines(cycleCount);
        return toDomain(saved, findLineEntities(cycleCount.getId()));
    }

    @Override
    public Optional<CycleCount> findById(UUID id) {
        return springDataRepository.findById(id).map(entity -> toDomain(entity, findLineEntities(id)));
    }

    @Override
    public PagedResult<CycleCount> findAll(CycleCountFilter filter, int page, int pageSize) {
        Specification<CycleCountJpaEntity> specification = CycleCountSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<CycleCountJpaEntity> result = springDataRepository.findAll(specification, pageable);
        List<CycleCount> items = result.getContent().stream()
                .map(entity -> toDomain(entity, findLineEntities(entity.getId())))
                .toList();
        return new PagedResult<>(items, result.getTotalElements());
    }

    private void saveLines(CycleCount cycleCount) {
        for (CycleCountLine line : cycleCount.getLines()) {
            CycleCountLineJpaEntity lineEntity = lineSpringDataRepository.findById(line.getId())
                    .map(existing -> {
                        existing.setCountedQuantity(line.getCountedQuantity());
                        return existing;
                    })
                    .orElseGet(() -> CycleCountLineJpaEntity.builder()
                            .id(line.getId())
                            .cycleCountId(cycleCount.getId())
                            .productId(line.getProductId())
                            .countedQuantity(line.getCountedQuantity())
                            .build());
            lineSpringDataRepository.save(lineEntity);
        }
    }

    private List<CycleCountLineJpaEntity> findLineEntities(UUID cycleCountId) {
        return lineSpringDataRepository.findByCycleCountId(cycleCountId);
    }

    private CycleCountJpaEntity applyTo(CycleCountJpaEntity entity, CycleCount cycleCount) {
        entity.setStatus(cycleCount.getStatus());
        return entity;
    }

    private CycleCountJpaEntity toNewEntity(CycleCount cycleCount) {
        return CycleCountJpaEntity.builder()
                .id(cycleCount.getId())
                .warehouseId(cycleCount.getWarehouseId())
                .scope(cycleCount.getScope())
                .status(cycleCount.getStatus())
                .build();
    }

    private CycleCount toDomain(CycleCountJpaEntity entity, List<CycleCountLineJpaEntity> lineEntities) {
        List<CycleCountLine> lines = lineEntities.stream().map(this::toDomainLine).toList();
        return new CycleCount(entity.getId(), entity.getWarehouseId(), entity.getScope(), entity.getStatus(), lines);
    }

    private CycleCountLine toDomainLine(CycleCountLineJpaEntity entity) {
        return new CycleCountLine(entity.getId(), entity.getProductId(), entity.getCountedQuantity());
    }
}
