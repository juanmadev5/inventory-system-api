package com.jm.inventory.api.cyclecount;

import com.jm.inventory.api.security.RequireManager;
import com.jm.inventory.api.security.RequireStaff;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.cyclecount.CycleCountService;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.cyclecount.CycleCount;
import com.jm.inventory.domain.cyclecount.CycleCountFilter;
import com.jm.inventory.domain.cyclecount.CycleCountStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Every action here has the class-level {@code @RequireStaff} as its floor - only approval
 * escalates to {@code @RequireManager}, see docs/specs/09-cycle-counts/requirements.md's
 * roles table.
 */
@RestController
@RequestMapping("/api/v1/cycle-counts")
@Tag(name = "CycleCounts")
@RequireStaff
public class CycleCountController {

    private final CycleCountService cycleCountService;

    public CycleCountController(CycleCountService cycleCountService) {
        this.cycleCountService = cycleCountService;
    }

    @GetMapping
    @Operation(summary = "List cycle count sessions.")
    @ApiResponse(responseCode = "200", description = "Paginated list of cycle count sessions")
    public PageResponse<CycleCountResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) CycleCountStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        CycleCountFilter filter = new CycleCountFilter(warehouseId, status);
        PagedResult<CycleCount> result = cycleCountService.list(filter, page, pageSize);
        return PageResponse.of(
                result.items().stream().map(CycleCountResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @PostMapping
    @Operation(summary = "Create an OPEN cycle count session.")
    @ApiResponse(responseCode = "201", description = "Cycle count created")
    @ApiResponse(responseCode = "409", description = "WAREHOUSE_INACTIVE")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    public ResponseEntity<CycleCountResponse> create(@Valid @RequestBody CreateCycleCountRequest request) {
        CycleCount created = cycleCountService.create(request.warehouseId(), request.productIds());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(CycleCountResponse.from(created));
    }

    @GetMapping("/{cycleCountId}")
    @Operation(summary = "Get a cycle count session by id, including its lines.")
    @ApiResponse(responseCode = "200", description = "Cycle count found")
    @ApiResponse(responseCode = "404", description = "Not found")
    public CycleCountResponse getById(@PathVariable UUID cycleCountId) {
        return CycleCountResponse.from(cycleCountService.get(cycleCountId));
    }

    @PostMapping("/{cycleCountId}/lines/{lineId}/count")
    @Operation(summary = "Record (or overwrite) this line's counted quantity. Only while OPEN.")
    @ApiResponse(responseCode = "200", description = "Count recorded")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "CYCLE_COUNT_NOT_OPEN")
    public CycleCountResponse recordCount(
            @PathVariable UUID cycleCountId, @PathVariable UUID lineId,
            @Valid @RequestBody RecordCycleCountLineRequest request) {
        return CycleCountResponse.from(
                cycleCountService.recordCount(cycleCountId, lineId, request.countedQuantity()));
    }

    @PostMapping("/{cycleCountId}/approval")
    @RequireManager
    @Operation(summary = "Transition OPEN -> APPROVED, posting one adjustment per line whose count differs from the live system quantity.")
    @ApiResponse(responseCode = "200", description = "Cycle count approved")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "CYCLE_COUNT_NOT_OPEN, CYCLE_COUNT_INCOMPLETE, or PRODUCT_INACTIVE")
    public CycleCountResponse approve(@PathVariable UUID cycleCountId) {
        return CycleCountResponse.from(cycleCountService.approve(cycleCountId));
    }

    @PostMapping("/{cycleCountId}/cancellation")
    @Operation(summary = "Transition OPEN -> CANCELLED, posting nothing.")
    @ApiResponse(responseCode = "200", description = "Cycle count cancelled")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "409", description = "CYCLE_COUNT_NOT_OPEN")
    public CycleCountResponse cancel(@PathVariable UUID cycleCountId) {
        return CycleCountResponse.from(cycleCountService.cancel(cycleCountId));
    }
}
