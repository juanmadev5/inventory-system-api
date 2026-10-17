package com.jm.inventory.domain.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

/**
 * One immutable record of a create/update against an audited entity - never updated or
 * deleted once written, same as {@code StockMovement}. {@code actorId} is nullable only
 * when a write happens with no authenticated caller in context (see
 * docs/specs/07-audit-trail/requirements.md); {@code oldValue} is {@code null} exactly when
 * {@code action} is {@link AuditAction#CREATED}.
 */
@Value
public class AuditLogEntry {
    UUID id;
    AuditEntityType entityType;
    UUID entityId;
    AuditAction action;
    String actorId;
    String actorUsername;
    Instant occurredAt;
    Map<String, Object> oldValue;
    Map<String, Object> newValue;
}
