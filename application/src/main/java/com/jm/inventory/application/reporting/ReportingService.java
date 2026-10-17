package com.jm.inventory.application.reporting;

import com.jm.inventory.application.common.PaginationDefaults;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.ProductRepository;
import com.jm.inventory.domain.common.PagedResult;
import com.jm.inventory.domain.purchasing.PurchaseOrderLineCostRepository;
import com.jm.inventory.domain.stock.StockLevel;
import com.jm.inventory.domain.stock.StockLevelFilter;
import com.jm.inventory.domain.stock.StockLevelRepository;
import com.jm.inventory.domain.stock.StockMovement;
import com.jm.inventory.domain.stock.StockMovementFilter;
import com.jm.inventory.domain.stock.StockMovementRepository;
import com.jm.inventory.domain.stock.StockMovementType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Three read-only, computed reports - no new persisted entity, no audit entries (same as
 * {@code StockAlertService}). See docs/specs/10-reporting/requirements.md for the
 * deliberate, documented limitations (page-scoped valuation total, unsorted aging rows).
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderLineCostRepository purchaseOrderLineCostRepository;

    public ReportingService(
            StockLevelRepository stockLevelRepository,
            ProductRepository productRepository,
            StockMovementRepository stockMovementRepository,
            PurchaseOrderLineCostRepository purchaseOrderLineCostRepository) {
        this.stockLevelRepository = stockLevelRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.purchaseOrderLineCostRepository = purchaseOrderLineCostRepository;
    }

    public InventoryValuationResult inventoryValuation(UUID warehouseId, Integer page, Integer pageSize) {
        PagedResult<StockLevel> stockLevels = stockLevelRepository.findAll(
                new StockLevelFilter(null, warehouseId),
                PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));

        BigDecimal pageTotalValue = BigDecimal.ZERO;
        int excludedCount = 0;
        List<InventoryValuationRow> rows = new ArrayList<>();
        for (StockLevel stockLevel : stockLevels.items()) {
            Product product = requireExistingProduct(stockLevel.getProductId());
            Optional<BigDecimal> unitCost = purchaseOrderLineCostRepository.averageUnitCostForProduct(stockLevel.getProductId());
            BigDecimal value = unitCost.map(cost -> cost.multiply(BigDecimal.valueOf(stockLevel.getQuantityOnHand()))).orElse(null);
            if (value != null) {
                pageTotalValue = pageTotalValue.add(value);
            } else {
                excludedCount++;
            }
            rows.add(new InventoryValuationRow(
                    stockLevel.getProductId(), stockLevel.getWarehouseId(), product.getCategoryId(),
                    stockLevel.getQuantityOnHand(), unitCost.orElse(null), value));
        }
        return new InventoryValuationResult(rows, stockLevels.totalCount(), pageTotalValue, excludedCount);
    }

    public StockMovementSummary movementSummary(
            UUID warehouseId, UUID productId, Instant occurredFrom, Instant occurredTo) {
        List<StockMovement> movements = stockMovementRepository.findAllMatching(
                new StockMovementFilter(productId, warehouseId, null, occurredFrom, occurredTo));

        Map<StockMovementType, StockMovementTypeTotals> totalsByType = new EnumMap<>(StockMovementType.class);
        for (StockMovementType type : StockMovementType.values()) {
            totalsByType.put(type, new StockMovementTypeTotals(0, 0));
        }
        for (StockMovement movement : movements) {
            StockMovementTypeTotals existing = totalsByType.get(movement.getType());
            totalsByType.put(movement.getType(),
                    new StockMovementTypeTotals(existing.count() + 1, existing.totalQuantity() + movement.getQuantity()));
        }
        return new StockMovementSummary(totalsByType);
    }

    public PagedResult<StockAgingRow> stockAging(UUID warehouseId, Instant asOf, Integer page, Integer pageSize) {
        Instant effectiveAsOf = asOf != null ? asOf : Instant.now();
        PagedResult<StockLevel> stockLevels = stockLevelRepository.findAll(
                new StockLevelFilter(null, warehouseId),
                PaginationDefaults.normalizePage(page), PaginationDefaults.normalizePageSize(pageSize));

        List<StockAgingRow> rows = stockLevels.items().stream()
                .map(stockLevel -> toAgingRow(stockLevel, effectiveAsOf))
                .toList();
        return new PagedResult<>(rows, stockLevels.totalCount());
    }

    private StockAgingRow toAgingRow(StockLevel stockLevel, Instant asOf) {
        Optional<Instant> lastMovementAt =
                stockMovementRepository.findMostRecentOccurredAt(stockLevel.getProductId(), stockLevel.getWarehouseId());
        Long daysSinceLastMovement = lastMovementAt.map(instant -> Duration.between(instant, asOf).toDays()).orElse(null);
        return new StockAgingRow(
                stockLevel.getProductId(), stockLevel.getWarehouseId(), stockLevel.getQuantityOnHand(),
                lastMovementAt.orElse(null), daysSinceLastMovement);
    }

    private Product requireExistingProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Product " + productId + " referenced by a StockLevel row must exist"));
    }
}
