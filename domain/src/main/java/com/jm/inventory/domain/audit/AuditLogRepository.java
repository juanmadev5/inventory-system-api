package com.jm.inventory.domain.audit;

import com.jm.inventory.domain.common.PagedResult;
import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository {

    AuditLogEntry save(AuditLogEntry entry);

    Optional<AuditLogEntry> findById(UUID id);

    PagedResult<AuditLogEntry> findAll(AuditLogFilter filter, int page, int pageSize);
}
