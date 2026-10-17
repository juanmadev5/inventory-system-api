package com.jm.inventory.api.supplier;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.supplier.SupplierService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.supplier.Supplier;
import com.jm.inventory.domain.supplier.SupplierFilter;
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
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Suppliers")
@RequireStaff
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @Operation(summary = "List suppliers.")
    @ApiResponse(responseCode = "200", description = "Paginated list of suppliers")
    public PageResponse<SupplierResponse> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<Supplier> result = supplierService.list(new SupplierFilter(name), page, pageSize);
        return PageResponse.of(
                result.items().stream().map(SupplierResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @RequireManager
    @Operation(summary = "Create a supplier.")
    @ApiResponse(responseCode = "201", description = "Supplier created")
    @ApiResponse(responseCode = "409", description = "SUPPLIER_NAME_ALREADY_EXISTS")
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody CreateSupplierRequest request) {
        Supplier created = supplierService.create(request.name(), request.email(), request.phone());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(SupplierResponse.from(created));
    }

    @GetMapping("/{supplierId}")
    @Operation(summary = "Get a supplier by id.")
    @ApiResponse(responseCode = "200", description = "Supplier found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public SupplierResponse getById(@PathVariable UUID supplierId) {
        return SupplierResponse.from(supplierService.get(supplierId));
    }

    @PatchMapping("/{supplierId}")
    @RequireManager
    @Operation(summary = "Partially update a supplier.")
    @ApiResponse(responseCode = "200", description = "Supplier updated")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "SUPPLIER_NAME_ALREADY_EXISTS")
    public SupplierResponse update(@PathVariable UUID supplierId, @Valid @RequestBody UpdateSupplierRequest request) {
        Supplier updated = supplierService.update(supplierId, request.name(), request.email(), request.phone());
        return SupplierResponse.from(updated);
    }
}
