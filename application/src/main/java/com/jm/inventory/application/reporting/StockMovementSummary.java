package com.jm.inventory.application.reporting;

import com.jm.inventory.domain.stock.StockMovementType;
import java.util.Map;

/** All six {@link StockMovementType} values always present - see
 * docs/specs/10-reporting/requirements.md. */
public record StockMovementSummary(Map<StockMovementType, StockMovementTypeTotals> totalsByType) {
}
