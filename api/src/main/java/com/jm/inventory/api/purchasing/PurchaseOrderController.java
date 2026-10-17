package com.jm.inventory.api.purchasing;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.purchasing.CreatePurchaseOrderLineCommand;
import com.jm.inventory.application.purchasing.PurchaseOrderService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.purchasing.PurchaseOrder;
import com.jm.inventory.domain.purchasing.PurchaseOrderFilter;
import com.jm.inventory.domain.purchasing.PurchaseOrderStatus;
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
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "PurchaseOrders")
@RequireStaff
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    @Operation(summary = "List purchase orders.")
    @ApiResponse(responseCode = "200", description = "Paginated list of purchase orders")
    public PageResponse<PurchaseOrderResponse> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PurchaseOrderFilter filter = new PurchaseOrderFilter(supplierId, warehouseId, status);
        PagedResult<PurchaseOrder> result = purchaseOrderService.list(filter, page, pageSize);
        return PageResponse.of(
                result.items().stream().map(PurchaseOrderResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @RequireManager
    @Operation(summary = "Create a purchase order in DRAFT status with its lines.")
    @ApiResponse(responseCode = "201", description = "Purchase order created")
    @ApiResponse(responseCode = "409", description = "WAREHOUSE_INACTIVE")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<PurchaseOrderResponse> create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        var lineCommands = request.lines().stream()
                .map(line -> new CreatePurchaseOrderLineCommand(line.productId(), line.quantityOrdered(), line.unitCost()))
                .toList();
        PurchaseOrder created = purchaseOrderService.create(
                request.supplierId(), request.warehouseId(), request.expectedDeliveryDate(), lineCommands);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(PurchaseOrderResponse.from(created));
    }

    @GetMapping("/{purchaseOrderId}")
    @Operation(summary = "Get a purchase order by id, including its lines.")
    @ApiResponse(responseCode = "200", description = "Purchase order found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public PurchaseOrderResponse getById(@PathVariable UUID purchaseOrderId) {
        return PurchaseOrderResponse.from(purchaseOrderService.get(purchaseOrderId));
    }

    @PatchMapping("/{purchaseOrderId}")
    @RequireManager
    @Operation(summary = "Update expectedDeliveryDate. Only while DRAFT.")
    @ApiResponse(responseCode = "200", description = "Purchase order updated")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "PURCHASE_ORDER_NOT_EDITABLE")
    public PurchaseOrderResponse update(
            @PathVariable UUID purchaseOrderId, @Valid @RequestBody UpdatePurchaseOrderRequest request) {
        PurchaseOrder updated =
                purchaseOrderService.updateExpectedDeliveryDate(purchaseOrderId, request.expectedDeliveryDate());
        return PurchaseOrderResponse.from(updated);
    }

    @PostMapping("/{purchaseOrderId}/placement")
    @RequireManager
    @Operation(summary = "Transition DRAFT -> ORDERED.")
    @ApiResponse(responseCode = "200", description = "Purchase order placed")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "PURCHASE_ORDER_NOT_DRAFT")
    public PurchaseOrderResponse place(@PathVariable UUID purchaseOrderId) {
        return PurchaseOrderResponse.from(purchaseOrderService.place(purchaseOrderId));
    }

    @PostMapping("/{purchaseOrderId}/cancellation")
    @RequireManager
    @Operation(summary = "Transition DRAFT or ORDERED -> CANCELLED.")
    @ApiResponse(responseCode = "200", description = "Purchase order cancelled")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "PURCHASE_ORDER_NOT_CANCELLABLE")
    public PurchaseOrderResponse cancel(@PathVariable UUID purchaseOrderId) {
        return PurchaseOrderResponse.from(purchaseOrderService.cancel(purchaseOrderId));
    }

    @PostMapping("/{purchaseOrderId}/lines/{lineId}/receipts")
    @Operation(summary = "Record receiving some or all of this line's remaining quantity. May auto-transition the order to RECEIVED.")
    @ApiResponse(responseCode = "200", description = "Receipt recorded")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "PURCHASE_ORDER_NOT_ORDERED, RECEIPT_EXCEEDS_ORDERED_QUANTITY, or PRODUCT_INACTIVE")
    public PurchaseOrderResponse receiveLine(
            @PathVariable UUID purchaseOrderId, @PathVariable UUID lineId,
            @Valid @RequestBody ReceivePurchaseOrderLineRequest request) {
        return PurchaseOrderResponse.from(
                purchaseOrderService.receiveLine(purchaseOrderId, lineId, request.quantity()));
    }
}
