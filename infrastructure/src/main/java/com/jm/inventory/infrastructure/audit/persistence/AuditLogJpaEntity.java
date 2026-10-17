package com.jm.inventory.infrastructure.audit.persistence;

import com.jm.inventory.domain.audit.AuditAction;
import com.jm.inventory.domain.audit.AuditEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only, same reasoning as {@code StockMovementJpaEntity}: no setters, no update path -
 * {@code AuditLogRepositoryAdapter.save} always inserts a fresh row.
 * {@code oldValue}/{@code newValue} are stored as native Postgres {@code jsonb} via
 * Hibernate's {@code @JdbcTypeCode(SqlTypes.JSON)} on a plain {@code Map} field - no extra
 * library needed.
 */
@Entity
@Table(name = "audit_log_entries")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> newValue;
}
