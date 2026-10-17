package com.jm.inventory.api.stock;

import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.stock.StockLevelService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.stock.StockLevel;
import com.jm.inventory.domain.stock.StockLevelFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only in this feature - see docs/specs/01-catalog-and-stock/requirements.md. Writes
 * arrive with the Stock Movements feature.
 */
@RestController
@RequestMapping("/api/v1/stock-levels")
@Tag(name = "StockLevels")
@RequireStaff
public class StockLevelController {

    private final StockLevelService stockLevelService;

    public StockLevelController(StockLevelService stockLevelService) {
        this.stockLevelService = stockLevelService;
    }

    @GetMapping
    @Operation(summary = "List current stock levels.")
    @ApiResponse(responseCode = "200", description = "Paginated list of stock levels")
    public PageResponse<StockLevelResponse> list(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<StockLevel> result =
                stockLevelService.list(new StockLevelFilter(productId, warehouseId), page, pageSize);
        return PageResponse.of(
                result.items().stream().map(StockLevelResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @GetMapping("/{stockLevelId}")
    @Operation(summary = "Get a stock level row by id.")
    @ApiResponse(responseCode = "200", description = "Stock level found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public StockLevelResponse getById(@PathVariable UUID stockLevelId) {
        return StockLevelResponse.from(stockLevelService.get(stockLevelId));
    }
}
