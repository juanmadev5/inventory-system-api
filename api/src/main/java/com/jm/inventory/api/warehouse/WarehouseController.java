package com.jm.inventory.api.warehouse;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.warehouse.WarehouseService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.warehouse.Warehouse;
import com.jm.inventory.domain.warehouse.WarehouseFilter;
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
@RequestMapping("/api/v1/warehouses")
@Tag(name = "Warehouses")
@RequireStaff
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    @Operation(summary = "List warehouses.")
    @ApiResponse(responseCode = "200", description = "Paginated list of warehouses")
    public PageResponse<WarehouseResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<Warehouse> result = warehouseService.list(new WarehouseFilter(active), page, pageSize);
        return PageResponse.of(
                result.items().stream().map(WarehouseResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @RequireManager
    @Operation(summary = "Create a warehouse.")
    @ApiResponse(responseCode = "201", description = "Warehouse created")
    @ApiResponse(responseCode = "409", description = "WAREHOUSE_NAME_ALREADY_EXISTS")
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody CreateWarehouseRequest request) {
        Warehouse created = warehouseService.create(request.name(), request.address());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(WarehouseResponse.from(created));
    }

    @GetMapping("/{warehouseId}")
    @Operation(summary = "Get a warehouse by id.")
    @ApiResponse(responseCode = "200", description = "Warehouse found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public WarehouseResponse getById(@PathVariable UUID warehouseId) {
        return WarehouseResponse.from(warehouseService.get(warehouseId));
    }

    @PatchMapping("/{warehouseId}")
    @RequireManager
    @Operation(summary = "Partially update a warehouse.")
    @ApiResponse(responseCode = "200", description = "Warehouse updated")
    @ApiResponse(responseCode = "404", description = "Not found")
    public WarehouseResponse update(
            @PathVariable UUID warehouseId, @Valid @RequestBody UpdateWarehouseRequest request) {
        Warehouse updated = warehouseService.update(
                warehouseId, request.name(), request.address(), request.active());
        return WarehouseResponse.from(updated);
    }
}
