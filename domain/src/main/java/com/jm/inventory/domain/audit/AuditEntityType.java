package com.jm.inventory.domain.audit;

/**
 * Every entity type whose creates/updates are recorded by
 * {@link AuditLogEntry} - see docs/specs/07-audit-trail/requirements.md for which entities
 * are (and deliberately are not, e.g. {@code StockMovement} - already its own ledger)
 * audited. Extended whenever a future feature adds another audited entity, same convention
 * as {@code StockMovementType}.
 */
public enum AuditEntityType {
    CATEGORY,
    PRODUCT,
    WAREHOUSE,
    SUPPLIER,
    PURCHASE_ORDER,
    REORDER_THRESHOLD,
    CUSTOMER,
    SALES_ORDER,
    CYCLE_COUNT,
    USER,
    WEBHOOK_SUBSCRIPTION
}
