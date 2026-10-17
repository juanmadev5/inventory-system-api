package com.jm.inventory.application.purchasing;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.stock.StockMovementRecordingService;
import com.jm.inventory.application.warehouse.ActiveWarehouseValidator;
import com.jm.inventory.application.webhooks.PurchaseOrderReceivedEventData;
import com.jm.inventory.application.webhooks.WebhookEventPublisher;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.purchasing.PurchaseOrder;
import com.jm.inventory.domain.purchasing.PurchaseOrderFilter;
import com.jm.inventory.domain.purchasing.PurchaseOrderLine;
import com.jm.inventory.domain.purchasing.PurchaseOrderLineNotFoundException;
import com.jm.inventory.domain.purchasing.PurchaseOrderNotCancellableException;
import com.jm.inventory.domain.purchasing.PurchaseOrderNotDraftException;
import com.jm.inventory.domain.purchasing.PurchaseOrderNotEditableException;
import com.jm.inventory.domain.purchasing.PurchaseOrderNotFoundException;
import com.jm.inventory.domain.purchasing.PurchaseOrderNotOrderedException;
import com.jm.inventory.domain.purchasing.PurchaseOrderRepository;
import com.jm.inventory.domain.purchasing.PurchaseOrderStatus;
import com.jm.inventory.domain.purchasing.ReceiptExceedsOrderedQuantityException;
import com.jm.inventory.domain.purchasing.UnknownSupplierReferenceException;
import com.jm.inventory.domain.stock.StockMovementType;
import com.jm.inventory.domain.stock.UnknownProductReferenceException;
import com.jm.inventory.domain.supplier.SupplierRepository;
import com.jm.inventory.domain.webhooks.WebhookEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receiving a line calls straight into {@link StockMovementRecordingService} - the same
 * transaction, same pessimistic lock on {@code StockLevel} - rather than re-implementing
 * any part of that feature's logic. See
 * docs/specs/03-suppliers-and-purchase-orders/requirements.md.
 */
@Service
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ActiveWarehouseValidator activeWarehouseValidator;
    private final StockMovementRecordingService stockMovementRecordingService;
    private final AuditLogService auditLogService;
    private final WebhookEventPublisher webhookEventPublisher;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            ActiveWarehouseValidator activeWarehouseValidator,
            StockMovementRecordingService stockMovementRecordingService,
            AuditLogService auditLogService,
            WebhookEventPublisher webhookEventPublisher) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.activeWarehouseValidator = activeWarehouseValidator;
        this.stockMovementRecordingService = stockMovementRecordingService;
        this.auditLogService = auditLogService;
        this.webhookEventPublisher = webhookEventPublisher;
    }

    public PurchaseOrder create(
            UUID supplierId, UUID warehouseId, LocalDate expectedDeliveryDate,
            List<CreatePurchaseOrderLineCommand> lineCommands) {
        if (supplierRepository.findById(supplierId).isEmpty()) {
            throw new UnknownSupplierReferenceException(supplierId);
        }
        activeWarehouseValidator.requireActive(warehouseId, "warehouseId");

        List<PurchaseOrderLine> lines = lineCommands.stream().map(this::toNewLine).toList();

        PurchaseOrder purchaseOrder = new PurchaseOrder(
                UUID.randomUUID(), supplierId, warehouseId, PurchaseOrderStatus.DRAFT, expectedDeliveryDate, lines);
        PurchaseOrder created = purchaseOrderRepository.save(purchaseOrder);
        auditLogService.recordCreated(AuditEntityType.PURCHASE_ORDER, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(UUID id) {
        return requireExisting(id);
    }

    @Transactional(readOnly = true)
    public PagedResult<PurchaseOrder> list(PurchaseOrderFilter filter, Integer page, Integer pageSize) {
        return purchaseOrderRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    public PurchaseOrder updateExpectedDeliveryDate(UUID id, LocalDate expectedDeliveryDate) {
        PurchaseOrder purchaseOrder = requireExisting(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new PurchaseOrderNotEditableException(id);
        }
        PurchaseOrder saved = purchaseOrderRepository.save(
                withStatusAndDate(purchaseOrder, purchaseOrder.getStatus(), expectedDeliveryDate));
        auditLogService.recordUpdated(AuditEntityType.PURCHASE_ORDER, id, purchaseOrder, saved);
        return saved;
    }

    public PurchaseOrder place(UUID id) {
        PurchaseOrder purchaseOrder = requireExisting(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new PurchaseOrderNotDraftException(id);
        }
        PurchaseOrder saved = purchaseOrderRepository.save(
                withStatusAndDate(purchaseOrder, PurchaseOrderStatus.ORDERED, purchaseOrder.getExpectedDeliveryDate()));
        auditLogService.recordUpdated(AuditEntityType.PURCHASE_ORDER, id, purchaseOrder, saved);
        return saved;
    }

    public PurchaseOrder cancel(UUID id) {
        PurchaseOrder purchaseOrder = requireExisting(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT && purchaseOrder.getStatus() != PurchaseOrderStatus.ORDERED) {
            throw new PurchaseOrderNotCancellableException(id);
        }
        PurchaseOrder saved = purchaseOrderRepository.save(
                withStatusAndDate(purchaseOrder, PurchaseOrderStatus.CANCELLED, purchaseOrder.getExpectedDeliveryDate()));
        auditLogService.recordUpdated(AuditEntityType.PURCHASE_ORDER, id, purchaseOrder, saved);
        return saved;
    }

    public PurchaseOrder receiveLine(UUID purchaseOrderId, UUID lineId, int quantity) {
        PurchaseOrder purchaseOrder = requireExisting(purchaseOrderId);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.ORDERED) {
            throw new PurchaseOrderNotOrderedException(purchaseOrderId);
        }
        PurchaseOrderLine line = purchaseOrder.getLines().stream()
                .filter(candidate -> candidate.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new PurchaseOrderLineNotFoundException(lineId));

        int newQuantityReceived = line.getQuantityReceived() + quantity;
        if (newQuantityReceived > line.getQuantityOrdered()) {
            throw new ReceiptExceedsOrderedQuantityException(lineId);
        }

        stockMovementRecordingService.recordSingleMovement(
                line.getProductId(), purchaseOrder.getWarehouseId(), StockMovementType.RECEIPT, quantity,
                "Purchase order " + purchaseOrder.getId(), Instant.now());

        List<PurchaseOrderLine> updatedLines = purchaseOrder.getLines().stream()
                .map(candidate -> candidate.getId().equals(lineId) ? withQuantityReceived(candidate, newQuantityReceived) : candidate)
                .toList();
        boolean everyLineFullyReceived =
                updatedLines.stream().allMatch(candidate -> candidate.getQuantityReceived() >= candidate.getQuantityOrdered());
        PurchaseOrderStatus newStatus = everyLineFullyReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.ORDERED;

        PurchaseOrder updated = new PurchaseOrder(
                purchaseOrder.getId(), purchaseOrder.getSupplierId(), purchaseOrder.getWarehouseId(), newStatus,
                purchaseOrder.getExpectedDeliveryDate(), updatedLines);
        PurchaseOrder saved = purchaseOrderRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.PURCHASE_ORDER, purchaseOrderId, purchaseOrder, saved);
        if (newStatus == PurchaseOrderStatus.RECEIVED) {
            webhookEventPublisher.publish(
                    WebhookEventType.PURCHASE_ORDER_RECEIVED,
                    new PurchaseOrderReceivedEventData(saved.getId(), saved.getSupplierId(), saved.getWarehouseId()));
        }
        return saved;
    }

    private PurchaseOrder requireExisting(UUID id) {
        return purchaseOrderRepository.findById(id).orElseThrow(() -> new PurchaseOrderNotFoundException(id));
    }

    private PurchaseOrderLine toNewLine(CreatePurchaseOrderLineCommand command) {
        if (productRepository.findById(command.productId()).isEmpty()) {
            throw new UnknownProductReferenceException(command.productId());
        }
        return new PurchaseOrderLine(UUID.randomUUID(), command.productId(), command.quantityOrdered(), 0, command.unitCost());
    }

    private PurchaseOrderLine withQuantityReceived(PurchaseOrderLine line, int quantityReceived) {
        return new PurchaseOrderLine(line.getId(), line.getProductId(), line.getQuantityOrdered(), quantityReceived, line.getUnitCost());
    }

    private PurchaseOrder withStatusAndDate(PurchaseOrder purchaseOrder, PurchaseOrderStatus status, LocalDate expectedDeliveryDate) {
        return new PurchaseOrder(
                purchaseOrder.getId(), purchaseOrder.getSupplierId(), purchaseOrder.getWarehouseId(), status,
                expectedDeliveryDate, purchaseOrder.getLines());
    }
}
