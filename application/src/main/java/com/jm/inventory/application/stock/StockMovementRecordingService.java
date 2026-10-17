package com.jm.inventory.application.stock;

import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.application.warehouse.ActiveWarehouseValidator;
import com.jm.inventory.application.webhooks.StockAlertActivatedEventData;
import com.jm.inventory.application.webhooks.WebhookEventPublisher;
import com.jm.inventory.domain.alerting.ReorderThreshold;
import com.jm.inventory.domain.alerting.ReorderThresholdRepository;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.stock.InsufficientStockException;
import com.jm.inventory.domain.stock.ProductInactiveException;
import com.jm.inventory.domain.stock.SameWarehouseTransferException;
import com.jm.inventory.domain.stock.StockLevel;
import com.jm.inventory.domain.stock.StockLevelRepository;
import com.jm.inventory.domain.stock.StockMovement;
import com.jm.inventory.domain.stock.StockMovementFilter;
import com.jm.inventory.domain.stock.StockMovementNotFoundException;
import com.jm.inventory.domain.stock.StockMovementRepository;
import com.jm.inventory.domain.stock.StockMovementType;
import com.jm.inventory.domain.stock.UnknownProductReferenceException;
import com.jm.inventory.domain.webhooks.WebhookEventType;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of {@link StockMovement} and {@link StockLevel} - every quantity change
 * goes through {@link #recordSingleMovement} or {@link #recordTransfer}, never a direct
 * repository call from elsewhere. See docs/specs/02-stock-movements/requirements.md.
 */
@Service
@Transactional
public class StockMovementRecordingService {

    private static final Set<StockMovementType> TYPES_REQUIRING_ACTIVE_PRODUCT =
            EnumSet.of(StockMovementType.RECEIPT, StockMovementType.ADJUSTMENT_INCREASE);

    private final StockMovementRepository stockMovementRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;
    private final ActiveWarehouseValidator activeWarehouseValidator;
    private final ReorderThresholdRepository reorderThresholdRepository;
    private final WebhookEventPublisher webhookEventPublisher;

    public StockMovementRecordingService(
            StockMovementRepository stockMovementRepository,
            StockLevelRepository stockLevelRepository,
            ProductRepository productRepository,
            ActiveWarehouseValidator activeWarehouseValidator,
            ReorderThresholdRepository reorderThresholdRepository,
            WebhookEventPublisher webhookEventPublisher) {
        this.stockMovementRepository = stockMovementRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.productRepository = productRepository;
        this.activeWarehouseValidator = activeWarehouseValidator;
        this.reorderThresholdRepository = reorderThresholdRepository;
        this.webhookEventPublisher = webhookEventPublisher;
    }

    public StockMovement recordSingleMovement(
            UUID productId, UUID warehouseId, StockMovementType type, int quantity, String reference,
            Instant occurredAt) {
        Product product = requireExistingProduct(productId);
        requireActiveProductFor(product, type);
        activeWarehouseValidator.requireActive(warehouseId, "warehouseId");

        applyDeltaOrThrow(productId, warehouseId, type, quantity);

        return stockMovementRepository.save(new StockMovement(
                UUID.randomUUID(), productId, warehouseId, type, quantity,
                occurredAt != null ? occurredAt : Instant.now(), reference, null));
    }

    public StockTransferResult recordTransfer(
            UUID productId, UUID sourceWarehouseId, UUID destinationWarehouseId, int quantity, String reference,
            Instant occurredAt) {
        if (sourceWarehouseId.equals(destinationWarehouseId)) {
            throw new SameWarehouseTransferException(destinationWarehouseId);
        }
        requireExistingProduct(productId);
        activeWarehouseValidator.requireActive(sourceWarehouseId, "sourceWarehouseId");
        activeWarehouseValidator.requireActive(destinationWarehouseId, "destinationWarehouseId");

        Instant effectiveOccurredAt = occurredAt != null ? occurredAt : Instant.now();

        applyDeltaOrThrow(productId, sourceWarehouseId, StockMovementType.TRANSFER_OUT, quantity);
        applyDeltaOrThrow(productId, destinationWarehouseId, StockMovementType.TRANSFER_IN, quantity);

        StockMovement outbound = stockMovementRepository.save(new StockMovement(
                UUID.randomUUID(), productId, sourceWarehouseId, StockMovementType.TRANSFER_OUT, quantity,
                effectiveOccurredAt, reference, null));
        StockMovement inbound = stockMovementRepository.save(new StockMovement(
                UUID.randomUUID(), productId, destinationWarehouseId, StockMovementType.TRANSFER_IN, quantity,
                effectiveOccurredAt, reference, outbound.getId()));

        return new StockTransferResult(outbound, inbound);
    }

    @Transactional(readOnly = true)
    public StockMovement get(UUID id) {
        return stockMovementRepository.findById(id).orElseThrow(() -> new StockMovementNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PagedResult<StockMovement> list(StockMovementFilter filter, Integer page, Integer pageSize) {
        return stockMovementRepository.findAll(
                filter, PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));
    }

    private Product requireExistingProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new UnknownProductReferenceException(productId));
    }

    private void requireActiveProductFor(Product product, StockMovementType type) {
        if (TYPES_REQUIRING_ACTIVE_PRODUCT.contains(type) && !product.isActive()) {
            throw new ProductInactiveException(product.getId());
        }
    }

    private void applyDeltaOrThrow(UUID productId, UUID warehouseId, StockMovementType type, int quantity) {
        StockLevel lockedLevel = stockLevelRepository.lockOrCreateForUpdate(productId, warehouseId);
        int previousQuantityOnHand = lockedLevel.getQuantityOnHand();
        int newQuantityOnHand = previousQuantityOnHand + type.sign() * quantity;
        if (newQuantityOnHand < 0) {
            throw new InsufficientStockException(productId, warehouseId);
        }
        stockLevelRepository.save(new StockLevel(
                lockedLevel.getId(), productId, warehouseId, newQuantityOnHand, lockedLevel.getQuantityReserved()));
        publishStockAlertIfNewlyCrossed(productId, warehouseId, previousQuantityOnHand, newQuantityOnHand);
    }

    /**
     * Only the *crossing* from at-or-above the reorder point down to below it is the event -
     * see docs/specs/14-webhooks/requirements.md's acceptance criterion 1. A product with no
     * configured threshold never fires one.
     */
    private void publishStockAlertIfNewlyCrossed(
            UUID productId, UUID warehouseId, int previousQuantityOnHand, int newQuantityOnHand) {
        Optional<ReorderThreshold> threshold = reorderThresholdRepository.findByProductIdAndWarehouseId(productId, warehouseId);
        if (threshold.isEmpty()) {
            return;
        }
        int reorderPoint = threshold.get().getReorderPoint();
        boolean newlyCrossed = previousQuantityOnHand >= reorderPoint && newQuantityOnHand < reorderPoint;
        if (newlyCrossed) {
            webhookEventPublisher.publish(
                    WebhookEventType.STOCK_ALERT_ACTIVATED,
                    new StockAlertActivatedEventData(productId, warehouseId, newQuantityOnHand, reorderPoint));
        }
    }

    public record StockTransferResult(StockMovement outboundMovement, StockMovement inboundMovement) {
    }
}
