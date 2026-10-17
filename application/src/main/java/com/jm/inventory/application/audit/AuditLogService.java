package com.jm.inventory.application.audit;

import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.audit.AuditAction;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.audit.AuditLogEntry;
import com.jm.inventory.domain.audit.AuditLogEntryNotFoundException;
import com.jm.inventory.domain.audit.AuditLogFilter;
import com.jm.inventory.domain.audit.AuditLogRepository;
import com.jm.inventory.domain.common.PagedResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Called by every service whose entity is audited (see
 * docs/specs/07-audit-trail/requirements.md for the list) right after persisting, inside
 * the same transaction. Snapshots are generic ({@link #snapshot}) rather than hand-built
 * per entity, so adding a field to an audited domain class never requires a matching
 * change here.
 */
@Service
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentActor currentActor;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, CurrentActor currentActor, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.currentActor = currentActor;
        this.objectMapper = objectMapper;
    }

    public void recordCreated(AuditEntityType entityType, UUID entityId, Object newEntity) {
        record(entityType, entityId, AuditAction.CREATED, null, newEntity);
    }

    public void recordUpdated(AuditEntityType entityType, UUID entityId, Object oldEntity, Object newEntity) {
        record(entityType, entityId, AuditAction.UPDATED, oldEntity, newEntity);
    }

    @Transactional(readOnly = true)
    public AuditLogEntry get(UUID id) {
        return auditLogRepository.findById(id).orElseThrow(() -> new AuditLogEntryNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogEntry> list(AuditLogFilter filter, Integer page, Integer pageSize) {
        return auditLogRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    private void record(AuditEntityType entityType, UUID entityId, AuditAction action, Object oldEntity, Object newEntity) {
        auditLogRepository.save(new AuditLogEntry(
                UUID.randomUUID(), entityType, entityId, action,
                currentActor.actorId(), currentActor.actorUsername(), Instant.now(),
                snapshot(oldEntity), snapshot(newEntity)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshot(Object entity) {
        return entity == null ? null : objectMapper.convertValue(entity, Map.class);
    }
}
