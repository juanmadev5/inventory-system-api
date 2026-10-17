package com.jm.inventory.api.alerting;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.alerting.ReorderThresholdService;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.alerting.ReorderThreshold;
import com.jm.inventory.domain.alerting.ReorderThresholdFilter;
import com.jm.inventory.domain.common.PagedResult;
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
@RequestMapping("/api/v1/reorder-thresholds")
@Tag(name = "ReorderThresholds")
@RequireStaff
public class ReorderThresholdController {

    private final ReorderThresholdService reorderThresholdService;

    public ReorderThresholdController(ReorderThresholdService reorderThresholdService) {
        this.reorderThresholdService = reorderThresholdService;
    }

    @GetMapping
    @Operation(summary = "List reorder thresholds.")
    @ApiResponse(responseCode = "200", description = "Paginated list of reorder thresholds")
    public PageResponse<ReorderThresholdResponse> list(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        PagedResult<ReorderThreshold> result =
                reorderThresholdService.list(new ReorderThresholdFilter(productId, warehouseId), page, pageSize);
        return PageResponse.of(
                result.items().stream().map(ReorderThresholdResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @RequireManager
    @Operation(summary = "Create a reorder threshold for a (productId, warehouseId) pair.")
    @ApiResponse(responseCode = "201", description = "Reorder threshold created")
    @ApiResponse(responseCode = "409", description = "REORDER_THRESHOLD_ALREADY_EXISTS")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<ReorderThresholdResponse> create(
            @Valid @RequestBody CreateReorderThresholdRequest request) {
        ReorderThreshold created = reorderThresholdService.create(
                request.productId(), request.warehouseId(), request.reorderPoint(), request.reorderQuantity());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(ReorderThresholdResponse.from(created));
    }

    @GetMapping("/{thresholdId}")
    @Operation(summary = "Get a reorder threshold by id.")
    @ApiResponse(responseCode = "200", description = "Reorder threshold found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public ReorderThresholdResponse getById(@PathVariable UUID thresholdId) {
        return ReorderThresholdResponse.from(reorderThresholdService.get(thresholdId));
    }

    @PatchMapping("/{thresholdId}")
    @RequireManager
    @Operation(summary = "Update reorderPoint and/or reorderQuantity.")
    @ApiResponse(responseCode = "200", description = "Reorder threshold updated")
    @ApiResponse(responseCode = "404", description = "Not found")
    public ReorderThresholdResponse update(
            @PathVariable UUID thresholdId, @Valid @RequestBody UpdateReorderThresholdRequest request) {
        ReorderThreshold updated =
                reorderThresholdService.update(thresholdId, request.reorderPoint(), request.reorderQuantity());
        return ReorderThresholdResponse.from(updated);
    }
}
