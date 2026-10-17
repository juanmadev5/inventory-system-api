package com.jm.inventory.application.reporting;

import java.math.BigDecimal;
import java.util.List;

/**
 * Not {@code domain.common.PagedResult} - this report carries page-scoped aggregates
 * (deliberately page-scoped, not a grand total, per
 * docs/specs/10-reporting/requirements.md's "Decision") that plain {@code PagedResult}
 * has no room for.
 */
public record InventoryValuationResult(
        List<InventoryValuationRow> items, long totalCount, BigDecimal pageTotalValue, int excludedCount) {
}
