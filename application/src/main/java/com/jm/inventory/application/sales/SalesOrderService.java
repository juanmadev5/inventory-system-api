package com.jm.inventory.application.sales;

import com.jm.inventory.application.audit.AuditLogService;
import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.stock.StockMovementRecordingService;
import com.jm.inventory.application.stock.StockReservationService;
import com.jm.inventory.application.warehouse.ActiveWarehouseValidator;
import com.jm.inventory.application.webhooks.SalesOrderShippedEventData;
import com.jm.inventory.application.webhooks.WebhookEventPublisher;
import com.jm.inventory.domain.audit.AuditEntityType;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.customer.CustomerRepository;
import com.jm.inventory.domain.sales.SalesOrder;
import com.jm.inventory.domain.sales.SalesOrderFilter;
import com.jm.inventory.domain.sales.SalesOrderLine;
import com.jm.inventory.domain.sales.SalesOrderLineNotFoundException;
import com.jm.inventory.domain.sales.SalesOrderNotCancellableException;
import com.jm.inventory.domain.sales.SalesOrderNotConfirmedException;
import com.jm.inventory.domain.sales.SalesOrderNotDraftException;
import com.jm.inventory.domain.sales.SalesOrderNotEditableException;
import com.jm.inventory.domain.sales.SalesOrderNotFoundException;
import com.jm.inventory.domain.sales.SalesOrderRepository;
import com.jm.inventory.domain.sales.SalesOrderStatus;
import com.jm.inventory.domain.sales.ShipmentExceedsOrderedQuantityException;
import com.jm.inventory.domain.sales.UnknownCustomerReferenceException;
import com.jm.inventory.domain.stock.StockMovementType;
import com.jm.inventory.domain.stock.UnknownProductReferenceException;
import com.jm.inventory.domain.webhooks.WebhookEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outbound symmetric twin of {@code PurchaseOrderService}. Confirming reserves stock via
 * {@link StockReservationService}; shipping a line releases that reservation and calls
 * straight into {@link StockMovementRecordingService} for the actual {@code SHIPMENT} - same
 * transaction, same pessimistic lock, never duplicated logic. See
 * docs/specs/08-sales-orders-and-customers/requirements.md.
 */
@Service
@Transactional
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ActiveWarehouseValidator activeWarehouseValidator;
    private final StockMovementRecordingService stockMovementRecordingService;
    private final StockReservationService stockReservationService;
    private final AuditLogService auditLogService;
    private final WebhookEventPublisher webhookEventPublisher;

    public SalesOrderService(
            SalesOrderRepository salesOrderRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            ActiveWarehouseValidator activeWarehouseValidator,
            StockMovementRecordingService stockMovementRecordingService,
            StockReservationService stockReservationService,
            AuditLogService auditLogService,
            WebhookEventPublisher webhookEventPublisher) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.activeWarehouseValidator = activeWarehouseValidator;
        this.stockMovementRecordingService = stockMovementRecordingService;
        this.stockReservationService = stockReservationService;
        this.auditLogService = auditLogService;
        this.webhookEventPublisher = webhookEventPublisher;
    }

    public SalesOrder create(
            UUID customerId, UUID warehouseId, LocalDate expectedShipDate,
            List<CreateSalesOrderLineCommand> lineCommands) {
        if (customerRepository.findById(customerId).isEmpty()) {
            throw new UnknownCustomerReferenceException(customerId);
        }
        activeWarehouseValidator.requireActive(warehouseId, "warehouseId");

        List<SalesOrderLine> lines = lineCommands.stream().map(this::toNewLine).toList();

        SalesOrder salesOrder = new SalesOrder(
                UUID.randomUUID(), customerId, warehouseId, SalesOrderStatus.DRAFT, expectedShipDate, lines);
        SalesOrder created = salesOrderRepository.save(salesOrder);
        auditLogService.recordCreated(AuditEntityType.SALES_ORDER, created.getId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public SalesOrder get(UUID id) {
        return requireExisting(id);
    }

    @Transactional(readOnly = true)
    public PagedResult<SalesOrder> list(SalesOrderFilter filter, Integer page, Integer pageSize) {
        return salesOrderRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    public SalesOrder updateExpectedShipDate(UUID id, LocalDate expectedShipDate) {
        SalesOrder salesOrder = requireExisting(id);
        if (salesOrder.getStatus() != SalesOrderStatus.DRAFT) {
            throw new SalesOrderNotEditableException(id);
        }
        SalesOrder saved = salesOrderRepository.save(
                withStatusAndDate(salesOrder, salesOrder.getStatus(), expectedShipDate));
        auditLogService.recordUpdated(AuditEntityType.SALES_ORDER, id, salesOrder, saved);
        return saved;
    }

    public SalesOrder confirm(UUID id) {
        SalesOrder salesOrder = requireExisting(id);
        if (salesOrder.getStatus() != SalesOrderStatus.DRAFT) {
            throw new SalesOrderNotDraftException(id);
        }
        for (SalesOrderLine line : salesOrder.getLines()) {
            stockReservationService.reserve(line.getProductId(), salesOrder.getWarehouseId(), line.getQuantityOrdered());
        }
        SalesOrder saved = salesOrderRepository.save(
                withStatusAndDate(salesOrder, SalesOrderStatus.CONFIRMED, salesOrder.getExpectedShipDate()));
        auditLogService.recordUpdated(AuditEntityType.SALES_ORDER, id, salesOrder, saved);
        return saved;
    }

    public SalesOrder cancel(UUID id) {
        SalesOrder salesOrder = requireExisting(id);
        if (salesOrder.getStatus() != SalesOrderStatus.DRAFT && salesOrder.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new SalesOrderNotCancellableException(id);
        }
        if (salesOrder.getStatus() == SalesOrderStatus.CONFIRMED) {
            for (SalesOrderLine line : salesOrder.getLines()) {
                int remainingReserved = line.getQuantityOrdered() - line.getQuantityShipped();
                stockReservationService.release(line.getProductId(), salesOrder.getWarehouseId(), remainingReserved);
            }
        }
        SalesOrder saved = salesOrderRepository.save(
                withStatusAndDate(salesOrder, SalesOrderStatus.CANCELLED, salesOrder.getExpectedShipDate()));
        auditLogService.recordUpdated(AuditEntityType.SALES_ORDER, id, salesOrder, saved);
        return saved;
    }

    public SalesOrder shipLine(UUID salesOrderId, UUID lineId, int quantity) {
        SalesOrder salesOrder = requireExisting(salesOrderId);
        if (salesOrder.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new SalesOrderNotConfirmedException(salesOrderId);
        }
        SalesOrderLine line = salesOrder.getLines().stream()
                .filter(candidate -> candidate.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new SalesOrderLineNotFoundException(lineId));

        int newQuantityShipped = line.getQuantityShipped() + quantity;
        if (newQuantityShipped > line.getQuantityOrdered()) {
            throw new ShipmentExceedsOrderedQuantityException(lineId);
        }

        stockReservationService.release(line.getProductId(), salesOrder.getWarehouseId(), quantity);
        stockMovementRecordingService.recordSingleMovement(
                line.getProductId(), salesOrder.getWarehouseId(), StockMovementType.SHIPMENT, quantity,
                "Sales order " + salesOrder.getId(), Instant.now());

        List<SalesOrderLine> updatedLines = salesOrder.getLines().stream()
                .map(candidate -> candidate.getId().equals(lineId) ? withQuantityShipped(candidate, newQuantityShipped) : candidate)
                .toList();
        boolean everyLineFullyShipped =
                updatedLines.stream().allMatch(candidate -> candidate.getQuantityShipped() >= candidate.getQuantityOrdered());
        SalesOrderStatus newStatus = everyLineFullyShipped ? SalesOrderStatus.SHIPPED : SalesOrderStatus.CONFIRMED;

        SalesOrder updated = new SalesOrder(
                salesOrder.getId(), salesOrder.getCustomerId(), salesOrder.getWarehouseId(), newStatus,
                salesOrder.getExpectedShipDate(), updatedLines);
        SalesOrder saved = salesOrderRepository.save(updated);
        auditLogService.recordUpdated(AuditEntityType.SALES_ORDER, salesOrderId, salesOrder, saved);
        if (newStatus == SalesOrderStatus.SHIPPED) {
            webhookEventPublisher.publish(
                    WebhookEventType.SALES_ORDER_SHIPPED,
                    new SalesOrderShippedEventData(saved.getId(), saved.getCustomerId(), saved.getWarehouseId()));
        }
        return saved;
    }

    private SalesOrder requireExisting(UUID id) {
        return salesOrderRepository.findById(id).orElseThrow(() -> new SalesOrderNotFoundException(id));
    }

    private SalesOrderLine toNewLine(CreateSalesOrderLineCommand command) {
        if (productRepository.findById(command.productId()).isEmpty()) {
            throw new UnknownProductReferenceException(command.productId());
        }
        return new SalesOrderLine(UUID.randomUUID(), command.productId(), command.quantityOrdered(), 0, command.unitPrice());
    }

    private SalesOrderLine withQuantityShipped(SalesOrderLine line, int quantityShipped) {
        return new SalesOrderLine(line.getId(), line.getProductId(), line.getQuantityOrdered(), quantityShipped, line.getUnitPrice());
    }

    private SalesOrder withStatusAndDate(SalesOrder salesOrder, SalesOrderStatus status, LocalDate expectedShipDate) {
        return new SalesOrder(
                salesOrder.getId(), salesOrder.getCustomerId(), salesOrder.getWarehouseId(), status,
                expectedShipDate, salesOrder.getLines());
    }
}
