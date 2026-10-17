package com.jm.inventory.domain.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogFilter(
        AuditEntityType entityType, UUID entityId, AuditAction action, Instant occurredFrom, Instant occurredTo) {
}
