package com.jm.inventory.application.stock;

import com.jm.inventory.domain.stock.InsufficientStockException;
import com.jm.inventory.domain.stock.StockLevel;
import com.jm.inventory.domain.stock.StockLevelRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of {@link StockLevel#getQuantityReserved()} - every reservation change goes
 * through {@link #reserve}/{@link #release}, never a direct repository call from elsewhere,
 * same discipline as {@code StockMovementRecordingService} for {@code quantityOnHand}. Built
 * on the same {@link StockLevelRepository#lockOrCreateForUpdate} pessimistic-lock primitive,
 * so the concurrency guarantee from docs/specs/02-stock-movements/requirements.md's criterion
 * 7 extends to reservations. See docs/specs/08-sales-orders-and-customers/requirements.md.
 */
@Service
@Transactional
public class StockReservationService {

    private final StockLevelRepository stockLevelRepository;

    public StockReservationService(StockLevelRepository stockLevelRepository) {
        this.stockLevelRepository = stockLevelRepository;
    }

    public void reserve(UUID productId, UUID warehouseId, int quantity) {
        StockLevel lockedLevel = stockLevelRepository.lockOrCreateForUpdate(productId, warehouseId);
        int quantityAvailable = lockedLevel.getQuantityOnHand() - lockedLevel.getQuantityReserved();
        if (quantity > quantityAvailable) {
            throw new InsufficientStockException(productId, warehouseId);
        }
        stockLevelRepository.save(new StockLevel(
                lockedLevel.getId(), productId, warehouseId,
                lockedLevel.getQuantityOnHand(), lockedLevel.getQuantityReserved() + quantity));
    }

    /**
     * Only ever called with a quantity the caller already knows is currently reserved (the
     * unshipped remainder of a line it owns) - an internal invariant, not a system boundary,
     * so no additional validation here. See the class Javadoc.
     */
    public void release(UUID productId, UUID warehouseId, int quantity) {
        StockLevel lockedLevel = stockLevelRepository.lockOrCreateForUpdate(productId, warehouseId);
        stockLevelRepository.save(new StockLevel(
                lockedLevel.getId(), productId, warehouseId,
                lockedLevel.getQuantityOnHand(), lockedLevel.getQuantityReserved() - quantity));
    }
}
