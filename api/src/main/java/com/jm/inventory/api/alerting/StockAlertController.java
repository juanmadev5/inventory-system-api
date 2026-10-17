package com.jm.inventory.api.alerting;

import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.alerting.StockAlertService;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.alerting.StockAlert;
import com.jm.inventory.domain.alerting.StockAlertFilter;
import com.jm.inventory.domain.common.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pull-only: no create/update/delete here, an alert is computed state, not a resource
 * anyone writes. See docs/specs/04-low-stock-alerts/requirements.md.
 */
@RestController
@RequestMapping("/api/v1/stock-alerts")
@Tag(name = "StockAlerts")
@RequireStaff
public class StockAlertController {

    private final StockAlertService stockAlertService;

    public StockAlertController(StockAlertService stockAlertService) {
        this.stockAlertService = stockAlertService;
    }

    @GetMapping
    @Operation(summary = "List every (productId, warehouseId) pair currently at or below its configured reorder point.")
    @ApiResponse(responseCode = "200", description = "Paginated list of stock alerts")
    public PageResponse<StockAlertResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<StockAlert> result = stockAlertService.list(new StockAlertFilter(warehouseId), page, pageSize);
        return PageResponse.of(
                result.items().stream().map(StockAlertResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }
}
