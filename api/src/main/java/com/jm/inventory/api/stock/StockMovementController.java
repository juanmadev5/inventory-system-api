package com.jm.inventory.api.stock;

import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.api.security.RoleAuthorities;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.stock.StockMovementRecordingService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.stock.StockMovement;
import com.jm.inventory.domain.stock.StockMovementFilter;
import com.jm.inventory.domain.stock.StockMovementType;
import com.jm.inventory.domain.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/stock-movements")
@Tag(name = "StockMovements")
@RequireStaff
public class StockMovementController {

    private final StockMovementRecordingService stockMovementRecordingService;

    public StockMovementController(StockMovementRecordingService stockMovementRecordingService) {
        this.stockMovementRecordingService = stockMovementRecordingService;
    }

    @GetMapping
    @Operation(summary = "List stock movements.")
    @ApiResponse(responseCode = "200", description = "Paginated list of stock movements")
    public PageResponse<StockMovementResponse> list(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        StockMovementFilter filter = new StockMovementFilter(productId, warehouseId, type, occurredFrom, occurredTo);
        PagedResult<StockMovement> result = stockMovementRecordingService.list(filter, page, pageSize);
        return PageResponse.of(
                result.items().stream().map(StockMovementResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @Operation(summary = "Record a single-warehouse movement (RECEIPT, SHIPMENT, ADJUSTMENT_INCREASE, ADJUSTMENT_DECREASE).")
    @ApiResponse(responseCode = "201", description = "Movement recorded")
    @ApiResponse(responseCode = "403",
            description = "FORBIDDEN - an ADJUSTMENT_INCREASE/ADJUSTMENT_DECREASE requires the ADMIN role")
    @ApiResponse(responseCode = "409", description = "PRODUCT_INACTIVE, WAREHOUSE_INACTIVE, or INSUFFICIENT_STOCK")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<StockMovementResponse> create(@Valid @RequestBody CreateStockMovementRequest request) {
        requireAdminForBareAdjustments(request.type());
        StockMovement created = stockMovementRecordingService.recordSingleMovement(
                request.productId(), request.warehouseId(), request.type().toDomain(), request.quantity(),
                request.reference(), request.occurredAt());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(StockMovementResponse.from(created));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Atomically record a TRANSFER_OUT at the source warehouse and a TRANSFER_IN at the destination.")
    @ApiResponse(responseCode = "201", description = "Both movements recorded")
    @ApiResponse(responseCode = "409", description = "PRODUCT_INACTIVE, WAREHOUSE_INACTIVE, or INSUFFICIENT_STOCK")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<StockTransferResponse> createTransfer(
            @Valid @RequestBody CreateStockTransferRequest request) {
        StockMovementRecordingService.StockTransferResult result = stockMovementRecordingService.recordTransfer(
                request.productId(), request.sourceWarehouseId(), request.destinationWarehouseId(),
                request.quantity(), request.reference(), request.occurredAt());
        return ResponseEntity.status(201).body(StockTransferResponse.from(result));
    }

    @GetMapping("/{movementId}")
    @Operation(summary = "Get a stock movement by id.")
    @ApiResponse(responseCode = "200", description = "Movement found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public StockMovementResponse getById(@PathVariable UUID movementId) {
        return StockMovementResponse.from(stockMovementRecordingService.get(movementId));
    }

    /**
     * {@code @PreAuthorize} cannot branch on request-body content without relying on
     * compiler debug metadata this project does not otherwise depend on, so the one
     * body-dependent rule in docs/specs/06-roles-and-permissions/requirements.md - a bare
     * adjustment requires {@code ADMIN}, everything else on this endpoint only requires the
     * class-level {@code @RequireStaff} - is checked explicitly here instead.
     */
    private void requireAdminForBareAdjustments(SingleMovementType type) {
        if (type.requiresAdminRole()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!RoleAuthorities.hasAtLeast(Role.ADMIN, authentication)) {
                throw new AccessDeniedException("A bare stock adjustment requires the ADMIN role.");
            }
        }
    }
}
