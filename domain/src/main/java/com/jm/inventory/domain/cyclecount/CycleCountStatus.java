package com.jm.inventory.domain.cyclecount;

/**
 * One active state ({@code OPEN}) - see docs/specs/09-cycle-counts/requirements.md's
 * "Decision" for why every mutating action shares the same precondition instead of a
 * multi-step chain like {@code PurchaseOrderStatus}.
 */
public enum CycleCountStatus {
    OPEN,
    APPROVED,
    CANCELLED
}
