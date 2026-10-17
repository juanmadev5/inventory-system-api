package com.jm.inventory.api.reporting;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.reporting.InventoryValuationResult;
import com.jm.inventory.application.reporting.ReportingService;
import com.jm.inventory.application.reporting.StockAgingRow;
import com.jm.inventory.domain.common.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint here requires {@code MANAGER} or above - no {@code STAFF} access, unlike
 * every other {@code GET} in this project. See
 * docs/specs/10-reporting/requirements.md's roles section for why.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reporting")
@RequireManager
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/inventory-valuation")
    @Operation(summary = "Paginated inventory valuation, one row per (productId, warehouseId).")
    @ApiResponse(responseCode = "200", description = "Paginated valuation rows")
    public InventoryValuationPageResponse inventoryValuation(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        InventoryValuationResult result = reportingService.inventoryValuation(warehouseId, page, pageSize);
        return InventoryValuationPageResponse.of(
                result.items().stream().map(InventoryValuationRowResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount(),
                result.pageTotalValue(),
                result.excludedCount());
    }

    @GetMapping("/stock-movement-summary")
    @Operation(summary = "Aggregated movement counts/quantities per StockMovementType. Not paginated.")
    @ApiResponse(responseCode = "200", description = "Movement summary")
    public StockMovementSummaryResponse stockMovementSummary(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredTo) {
        return StockMovementSummaryResponse.from(
                reportingService.movementSummary(warehouseId, productId, occurredFrom, occurredTo));
    }

    @GetMapping("/stock-aging")
    @Operation(summary = "Paginated stock aging, one row per (productId, warehouseId). Not pre-sorted by staleness.")
    @ApiResponse(responseCode = "200", description = "Paginated aging rows")
    public PageResponse<StockAgingRowResponse> stockAging(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Instant asOf,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<StockAgingRow> result = reportingService.stockAging(warehouseId, asOf, page, pageSize);
        return PageResponse.of(
                result.items().stream().map(StockAgingRowResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }
}
