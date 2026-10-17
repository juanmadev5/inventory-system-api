package com.jm.inventory.application.alerting;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.alerting.ReorderThreshold;
import com.jm.inventory.domain.alerting.ReorderThresholdAlreadyExistsException;
import com.jm.inventory.domain.alerting.ReorderThresholdFilter;
import com.jm.inventory.domain.alerting.ReorderThresholdNotFoundException;
import com.jm.inventory.domain.alerting.ReorderThresholdRepository;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.stock.UnknownProductReferenceException;
import com.jm.inventory.domain.stock.UnknownWarehouseReferenceException;
import com.jm.inventory.domain.warehouse.WarehouseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReorderThresholdService {

    private final ReorderThresholdRepository reorderThresholdRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditLogService auditLogService;

    public ReorderThresholdService(
            ReorderThresholdRepository reorderThresholdRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            AuditLogService auditLogService) {
        this.reorderThresholdRepository = reorderThresholdRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.auditLogService = auditLogService;
    }

    public ReorderThreshold create(UUID productId, UUID warehouseId, int reorderPoint, int reorderQuantity) {
        if (productRepository.findById(productId).isEmpty()) {
            throw new UnknownProductReferenceException(productId);
        }
        if (warehouseRepository.findById(warehouseId).isEmpty()) {
            throw new UnknownWarehouseReferenceException(warehouseId, "warehouseId");
        }
        if (reorderThresholdRepository.findByProductIdAndWarehouseId(productId, warehouseId).isPresent()) {
            throw new ReorderThresholdAlreadyExistsException(productId, warehouseId);
        }
        ReorderThreshold created = reorderThresholdRepository.save(
                new ReorderThreshold(UUID.randomUUID(), productId, warehouseId, reorderPoint, reorderQuantity));
        auditLogService.recordCreated(AuditEntityType.REORDER_THRESHOLD, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public ReorderThreshold get(UUID id) {
        return requireExisting(id);
    }

    public ReorderThreshold update(UUID id, Integer reorderPoint, Integer reorderQuantity) {
        ReorderThreshold existing = requireExisting(id);
        ReorderThreshold updated = new ReorderThreshold(
                id,
                existing.getProductId(),
                existing.getWarehouseId(),
                reorderPoint != null ? reorderPoint : existing.getReorderPoint(),
                reorderQuantity != null ? reorderQuantity : existing.getReorderQuantity());
        ReorderThreshold saved = reorderThresholdRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.REORDER_THRESHOLD, id, existing, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PagedResult<ReorderThreshold> list(ReorderThresholdFilter filter, Integer page, Integer pageSize) {
        return reorderThresholdRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    private ReorderThreshold requireExisting(UUID id) {
        return reorderThresholdRepository.findById(id).orElseThrow(() -> new ReorderThresholdNotFoundException(id));
    }
}
