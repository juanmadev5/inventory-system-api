package com.jm.inventory.api.reporting;

import com.jm.inventory.application.reporting.StockMovementSummary;
import com.jm.inventory.application.reporting.StockMovementTypeTotals;
import com.jm.inventory.domain.stock.StockMovementType;
import java.util.Map;

public record StockMovementSummaryResponse(Map<StockMovementType, StockMovementTypeTotals> totalsByType) {

    public static StockMovementSummaryResponse from(StockMovementSummary summary) {
        return new StockMovementSummaryResponse(summary.totalsByType());
    }
}
