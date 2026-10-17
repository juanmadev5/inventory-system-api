package com.jm.inventory.domain.webhooks;

/**
 * The fixed, global set of events a webhook subscription can subscribe to - see
 * docs/specs/14-webhooks/requirements.md. Grows the same way {@code AuditEntityType} does:
 * a future feature adds its own value here when it needs one, never a custom event name.
 */
public enum WebhookEventType {
    STOCK_ALERT_ACTIVATED,
    SALES_ORDER_SHIPPED,
    PURCHASE_ORDER_RECEIVED,
    CYCLE_COUNT_APPROVED
}
