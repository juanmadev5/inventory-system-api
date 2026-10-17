package com.jm.inventory.domain.purchasing;

/**
 * {@code RECEIVED} is never set by a direct request - it's the automatic result of every
 * line reaching {@code quantityReceived == quantityOrdered}. See
 * docs/specs/03-suppliers-and-purchase-orders/requirements.md's state machine.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    RECEIVED,
    CANCELLED
}
