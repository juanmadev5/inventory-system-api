package com.jm.inventory.domain.cyclecount;

/**
 * Set at creation from whether the request provided {@code productIds} - the client never
 * passes this directly. See docs/specs/09-cycle-counts/requirements.md's "Decision".
 */
public enum CycleCountScope {
    FULL,
    SELECTION
}
