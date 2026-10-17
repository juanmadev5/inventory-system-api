package com.jm.inventory.api.sales;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.sales.CreateSalesOrderLineCommand;
import com.jm.inventory.application.sales.SalesOrderService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.sales.SalesOrder;
import com.jm.inventory.domain.sales.SalesOrderFilter;
import com.jm.inventory.domain.sales.SalesOrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/sales-orders")
@Tag(name = "SalesOrders")
@RequireStaff
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @GetMapping
    @Operation(summary = "List sales orders.")
    @ApiResponse(responseCode = "200", description = "Paginated list of sales orders")
    public PageResponse<SalesOrderResponse> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        SalesOrderFilter filter = new SalesOrderFilter(customerId, warehouseId, status);
        PagedResult<SalesOrder> result = salesOrderService.list(filter, page, pageSize);
        return PageResponse.of(
                result.items().stream().map(SalesOrderResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @RequireManager
    @Operation(summary = "Create a sales order in DRAFT status with its lines.")
    @ApiResponse(responseCode = "201", description = "Sales order created")
    @ApiResponse(responseCode = "409", description = "WAREHOUSE_INACTIVE")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<SalesOrderResponse> create(@Valid @RequestBody CreateSalesOrderRequest request) {
        var lineCommands = request.lines().stream()
                .map(line -> new CreateSalesOrderLineCommand(line.productId(), line.quantityOrdered(), line.unitPrice()))
                .toList();
        SalesOrder created = salesOrderService.create(
                request.customerId(), request.warehouseId(), request.expectedShipDate(), lineCommands);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(SalesOrderResponse.from(created));
    }

    @GetMapping("/{salesOrderId}")
    @Operation(summary = "Get a sales order by id, including its lines.")
    @ApiResponse(responseCode = "200", description = "Sales order found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public SalesOrderResponse getById(@PathVariable UUID salesOrderId) {
        return SalesOrderResponse.from(salesOrderService.get(salesOrderId));
    }

    @PatchMapping("/{salesOrderId}")
    @RequireManager
    @Operation(summary = "Update expectedShipDate. Only while DRAFT.")
    @ApiResponse(responseCode = "200", description = "Sales order updated")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "SALES_ORDER_NOT_EDITABLE")
    public SalesOrderResponse update(
            @PathVariable UUID salesOrderId, @Valid @RequestBody UpdateSalesOrderRequest request) {
        SalesOrder updated = salesOrderService.updateExpectedShipDate(salesOrderId, request.expectedShipDate());
        return SalesOrderResponse.from(updated);
    }

    @PostMapping("/{salesOrderId}/confirmation")
    @RequireManager
    @Operation(summary = "Transition DRAFT -> CONFIRMED, reserving stock for every line.")
    @ApiResponse(responseCode = "200", description = "Sales order confirmed")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "SALES_ORDER_NOT_DRAFT or INSUFFICIENT_STOCK")
    public SalesOrderResponse confirm(@PathVariable UUID salesOrderId) {
        return SalesOrderResponse.from(salesOrderService.confirm(salesOrderId));
    }

    @PostMapping("/{salesOrderId}/cancellation")
    @RequireManager
    @Operation(summary = "Transition DRAFT or CONFIRMED -> CANCELLED, releasing any remaining reservation.")
    @ApiResponse(responseCode = "200", description = "Sales order cancelled")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "SALES_ORDER_NOT_CANCELLABLE")
    public SalesOrderResponse cancel(@PathVariable UUID salesOrderId) {
        return SalesOrderResponse.from(salesOrderService.cancel(salesOrderId));
    }

    @PostMapping("/{salesOrderId}/lines/{lineId}/shipments")
    @Operation(summary = "Record shipping some or all of this line's remaining quantity. May auto-transition the order to SHIPPED.")
    @ApiResponse(responseCode = "200", description = "Shipment recorded")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "SALES_ORDER_NOT_CONFIRMED or SHIPMENT_EXCEEDS_ORDERED_QUANTITY")
    public SalesOrderResponse shipLine(
            @PathVariable UUID salesOrderId, @PathVariable UUID lineId,
            @Valid @RequestBody ShipSalesOrderLineRequest request) {
        return SalesOrderResponse.from(salesOrderService.shipLine(salesOrderId, lineId, request.quantity()));
    }
}
