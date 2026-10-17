package com.jm.inventory.application.cyclecount;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.stock.StockMovementRecordingService;
import com.jm.inventory.application.warehouse.ActiveWarehouseValidator;
import com.jm.inventory.application.webhooks.CycleCountApprovedEventData;
import com.jm.inventory.application.webhooks.WebhookEventPublisher;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.cyclecount.CycleCount;
import com.jm.inventory.domain.cyclecount.CycleCountFilter;
import com.jm.inventory.domain.cyclecount.CycleCountIncompleteException;
import com.jm.inventory.domain.cyclecount.CycleCountLine;
import com.jm.inventory.domain.cyclecount.CycleCountLineNotFoundException;
import com.jm.inventory.domain.cyclecount.CycleCountNotFoundException;
import com.jm.inventory.domain.cyclecount.CycleCountNotOpenException;
import com.jm.inventory.domain.cyclecount.CycleCountRepository;
import com.jm.inventory.domain.cyclecount.CycleCountScope;
import com.jm.inventory.domain.cyclecount.CycleCountStatus;
import com.jm.inventory.domain.stock.StockLevel;
import com.jm.inventory.domain.stock.StockLevelRepository;
import com.jm.inventory.domain.stock.StockMovementType;
import com.jm.inventory.domain.stock.UnknownProductReferenceException;
import com.jm.inventory.domain.webhooks.WebhookEventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * See docs/specs/09-cycle-counts/requirements.md. Approval reads the live
 * {@code quantityOnHand} through {@link StockLevelRepository#lockOrCreateForUpdate} - the
 * same pessimistic lock {@code StockMovementRecordingService} re-acquires (re-entrant within
 * the same transaction) when posting the resulting adjustment - so the variance always
 * reflects the system's quantity at the moment of approval, never a stale snapshot.
 */
@Service
@Transactional
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;
    private final ActiveWarehouseValidator activeWarehouseValidator;
    private final StockMovementRecordingService stockMovementRecordingService;
    private final AuditLogService auditLogService;
    private final WebhookEventPublisher webhookEventPublisher;

    public CycleCountService(
            CycleCountRepository cycleCountRepository,
            StockLevelRepository stockLevelRepository,
            ProductRepository productRepository,
            ActiveWarehouseValidator activeWarehouseValidator,
            StockMovementRecordingService stockMovementRecordingService,
            AuditLogService auditLogService,
            WebhookEventPublisher webhookEventPublisher) {
        this.cycleCountRepository = cycleCountRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.productRepository = productRepository;
        this.activeWarehouseValidator = activeWarehouseValidator;
        this.stockMovementRecordingService = stockMovementRecordingService;
        this.webhookEventPublisher = webhookEventPublisher;
        this.auditLogService = auditLogService;
    }

    public CycleCount create(UUID warehouseId, List<UUID> productIds) {
        activeWarehouseValidator.requireActive(warehouseId, "warehouseId");

        CycleCountScope scope = productIds != null ? CycleCountScope.SELECTION : CycleCountScope.FULL;
        List<UUID> productIdsToCount = productIds != null
                ? productIds
                : stockLevelRepository.findProductIdsByWarehouseId(warehouseId);
        List<CycleCountLine> lines = productIdsToCount.stream().map(this::toNewLine).toList();

        CycleCount created = cycleCountRepository.save(
                new CycleCount(UUID.randomUUID(), warehouseId, scope, CycleCountStatus.OPEN, lines));
        auditLogService.recordCreated(AuditEntityType.CYCLE_COUNT, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public CycleCount get(UUID id) {
        return requireExisting(id);
    }

    @Transactional(readOnly = true)
    public PagedResult<CycleCount> list(CycleCountFilter filter, Integer page, Integer pageSize) {
        return cycleCountRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    public CycleCount recordCount(UUID cycleCountId, UUID lineId, int countedQuantity) {
        CycleCount cycleCount = requireExisting(cycleCountId);
        requireOpen(cycleCount);

        List<CycleCountLine> updatedLines = cycleCount.getLines().stream()
                .map(line -> line.getId().equals(lineId) ? withCountedQuantity(line, countedQuantity) : line)
                .toList();
        if (updatedLines.stream().noneMatch(line -> line.getId().equals(lineId))) {
            throw new CycleCountLineNotFoundException(lineId);
        }

        CycleCount updated = new CycleCount(
                cycleCount.getId(), cycleCount.getWarehouseId(), cycleCount.getScope(), cycleCount.getStatus(), updatedLines);
        CycleCount saved = cycleCountRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.CYCLE_COUNT, cycleCountId, cycleCount, saved);
        return saved;
    }

    public CycleCount approve(UUID id) {
        CycleCount cycleCount = requireExisting(id);
        requireOpen(cycleCount);
        if (cycleCount.getLines().stream().anyMatch(line -> line.getCountedQuantity() == null)) {
            throw new CycleCountIncompleteException(id);
        }

        for (CycleCountLine line : cycleCount.getLines()) {
            postAdjustmentIfNeeded(cycleCount, line);
        }

        CycleCount updated = new CycleCount(
                cycleCount.getId(), cycleCount.getWarehouseId(), cycleCount.getScope(), CycleCountStatus.APPROVED,
                cycleCount.getLines());
        CycleCount saved = cycleCountRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.CYCLE_COUNT, id, cycleCount, saved);
        webhookEventPublisher.publish(
                WebhookEventType.CYCLE_COUNT_APPROVED, new CycleCountApprovedEventData(saved.getId(), saved.getWarehouseId()));
        return saved;
    }

    public CycleCount cancel(UUID id) {
        CycleCount cycleCount = requireExisting(id);
        requireOpen(cycleCount);

        CycleCount updated = new CycleCount(
                cycleCount.getId(), cycleCount.getWarehouseId(), cycleCount.getScope(), CycleCountStatus.CANCELLED,
                cycleCount.getLines());
        CycleCount saved = cycleCountRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.CYCLE_COUNT, id, cycleCount, saved);
        return saved;
    }

    private void postAdjustmentIfNeeded(CycleCount cycleCount, CycleCountLine line) {
        StockLevel current = stockLevelRepository.lockOrCreateForUpdate(line.getProductId(), cycleCount.getWarehouseId());
        int variance = line.getCountedQuantity() - current.getQuantityOnHand();
        if (variance == 0) {
            return;
        }
        StockMovementType type = variance > 0 ? StockMovementType.ADJUSTMENT_INCREASE : StockMovementType.ADJUSTMENT_DECREASE;
        stockMovementRecordingService.recordSingleMovement(
                line.getProductId(), cycleCount.getWarehouseId(), type, Math.abs(variance),
                "Cycle count " + cycleCount.getId(), Instant.now());
    }

    private CycleCount requireExisting(UUID id) {
        return cycleCountRepository.findById(id).orElseThrow(() -> new CycleCountNotFoundException(id));
    }

    private void requireOpen(CycleCount cycleCount) {
        if (cycleCount.getStatus() != CycleCountStatus.OPEN) {
            throw new CycleCountNotOpenException(cycleCount.getId());
        }
    }

    private CycleCountLine toNewLine(UUID productId) {
        if (productRepository.findById(productId).isEmpty()) {
            throw new UnknownProductReferenceException(productId);
        }
        return new CycleCountLine(UUID.randomUUID(), productId, null);
    }

    private CycleCountLine withCountedQuantity(CycleCountLine line, int countedQuantity) {
        return new CycleCountLine(line.getId(), line.getProductId(), countedQuantity);
    }
}
