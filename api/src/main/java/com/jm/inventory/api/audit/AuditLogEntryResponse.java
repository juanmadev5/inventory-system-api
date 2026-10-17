package com.jm.inventory.api.audit;

import com.jm.inventory.domain.audit.AuditAction;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.audit.AuditLogEntry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogEntryResponse(
        UUID id,
        AuditEntityType entityType,
        UUID entityId,
        AuditAction action,
        String actorId,
        String actorUsername,
        Instant occurredAt,
        Map<String, Object> oldValue,
        Map<String, Object> newValue) {

    public static AuditLogEntryResponse from(AuditLogEntry entry) {
        return new AuditLogEntryResponse(
                entry.getId(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getAction(),
                entry.getActorId(),
                entry.getActorUsername(),
                entry.getOccurredAt(),
                entry.getOldValue(),
                entry.getNewValue());
    }
}
