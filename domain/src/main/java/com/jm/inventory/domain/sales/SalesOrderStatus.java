package com.jm.inventory.domain.sales;

/**
 * {@code SHIPPED} is never set by a direct request - it's the automatic result of every
 * line reaching {@code quantityShipped == quantityOrdered}. See
 * docs/specs/08-sales-orders-and-customers/requirements.md's state machine.
 */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    SHIPPED,
    CANCELLED
}
