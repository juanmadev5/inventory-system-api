package com.jm.inventory.api.audit;

import com.jm.inventory.api.security.RequireAdmin;
import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PageResponse;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.audit.AuditAction;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.audit.AuditLogEntry;
import com.jm.inventory.domain.audit.AuditLogFilter;
import com.jm.inventory.domain.common.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No create/update/delete here, same as {@code StockAlertController} - every row is written
 * internally by {@code AuditLogService} on behalf of the service it accompanies, never
 * directly by a client. See docs/specs/07-audit-trail/requirements.md.
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit")
@RequireAdmin
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @Operation(summary = "List audit log entries. Requires ADMIN.")
    @ApiResponse(responseCode = "200", description = "Paginated list of audit entries")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    public PageResponse<AuditLogEntryResponse> list(
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        AuditLogFilter filter = new AuditLogFilter(entityType, entityId, action, occurredFrom, occurredTo);
        PagedResult<AuditLogEntry> result = auditLogService.list(filter, page, pageSize);
        return PageResponse.of(
                result.items().stream().map(AuditLogEntryResponse::from).toList(),
                PaginationDefaults.normalizePage(page),
                PaginationDefaults.normalizePageSize(pageSize),
                result.totalCount());
    }

    @GetMapping("/{auditLogEntryId}")
    @Operation(summary = "Get an audit log entry by id. Requires ADMIN.")
    @ApiResponse(responseCode = "200", description = "Audit entry found")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @ApiResponse(responseCode = "404", description = "Not found")
    public AuditLogEntryResponse getById(@PathVariable UUID auditLogEntryId) {
        return AuditLogEntryResponse.from(auditLogService.get(auditLogEntryId));
    }
}
