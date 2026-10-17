package com.jm.inventory.api.reporting;

import java.math.BigDecimal;
import java.util.List;

/**
 * Same envelope shape as {@code PageResponse}, plus the page-scoped aggregates this one
 * report carries - see docs/specs/10-reporting/requirements.md's "Decision" for why
 * {@code pageTotalValue} is not a grand total across every page.
 */
public record InventoryValuationPageResponse(
        List<InventoryValuationRowResponse> items, int page, int pageSize, long totalCount, int totalPages,
        BigDecimal pageTotalValue, int excludedCount) {

    public static InventoryValuationPageResponse of(
            List<InventoryValuationRowResponse> items, int page, int pageSize, long totalCount,
            BigDecimal pageTotalValue, int excludedCount) {
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        return new InventoryValuationPageResponse(items, page, pageSize, totalCount, totalPages, pageTotalValue, excludedCount);
    }
}
