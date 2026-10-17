package com.jm.inventory.infrastructure.audit;

import com.jm.inventory.domain.audit.AuditLogEntry;
import com.jm.inventory.domain.audit.AuditLogFilter;
import com.jm.inventory.domain.audit.AuditLogRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.infrastructure.audit.persistence.AuditLogJpaEntity;
import com.jm.inventory.infrastructure.audit.persistence.AuditLogSpecifications;
import com.jm.inventory.infrastructure.audit.persistence.AuditLogSpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Append-only: {@link #save} is always a fresh insert, same reasoning as
 * {@code StockMovementRepositoryAdapter} - an audit entry is never loaded and mutated.
 */
@Component
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final AuditLogSpringDataRepository springDataRepository;

    public AuditLogRepositoryAdapter(AuditLogSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        AuditLogJpaEntity entity = AuditLogJpaEntity.builder()
                .id(entry.getId())
                .entityType(entry.getEntityType())
                .entityId(entry.getEntityId())
                .action(entry.getAction())
                .actorId(entry.getActorId())
                .actorUsername(entry.getActorUsername())
                .occurredAt(entry.getOccurredAt())
                .oldValue(entry.getOldValue())
                .newValue(entry.getNewValue())
                .build();
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<AuditLogEntry> findById(UUID id) {
        return springDataRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PagedResult<AuditLogEntry> findAll(AuditLogFilter filter, int page, int pageSize) {
        Specification<AuditLogJpaEntity> specification = AuditLogSpecifications.matching(filter);
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("occurredAt").descending());
        Page<AuditLogJpaEntity> result = springDataRepository.findAll(specification, pageable);
        return new PagedResult<>(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements());
    }

    private AuditLogEntry toDomain(AuditLogJpaEntity entity) {
        return new AuditLogEntry(
                entity.getId(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getAction(),
                entity.getActorId(),
                entity.getActorUsername(),
                entity.getOccurredAt(),
                entity.getOldValue(),
                entity.getNewValue());
    }
}
