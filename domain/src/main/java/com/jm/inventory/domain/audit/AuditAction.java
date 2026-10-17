package com.jm.inventory.domain.audit;

/**
 * Deliberately just these two, never one value per state transition (e.g. no
 * {@code PLACED}/{@code CANCELLED}) - the before/after snapshots on {@link AuditLogEntry}
 * already show what changed, so this set stays small and stable. See
 * docs/specs/07-audit-trail/requirements.md.
 */
public enum AuditAction {
    CREATED,
    UPDATED
}
