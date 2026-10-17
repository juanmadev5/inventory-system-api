package com.jm.inventory.api.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.jm.inventory.application.catalog.CategoryService;
import com.jm.inventory.application.catalog.ProductService;
import com.jm.inventory.application.stock.StockLevelService;
import com.jm.inventory.application.stock.StockMovementRecordingService;
import com.jm.inventory.application.stock.StockReservationService;
import com.jm.inventory.application.warehouse.WarehouseService;
import com.jm.inventory.domain.catalog.Category;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.UnitOfMeasure;
import com.jm.inventory.domain.stock.InsufficientStockException;
import com.jm.inventory.domain.stock.StockLevelFilter;
import com.jm.inventory.domain.stock.StockMovementType;
import com.jm.inventory.domain.warehouse.Warehouse;
import java.time.Instant;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves docs/specs/08-sales-orders-and-customers/requirements.md's concurrency claim: two
 * reservations racing against the same {@code (productId, warehouseId)} must not both read
 * the same stale available quantity and both succeed when only one should - same guarantee,
 * same mechanism ({@code StockLevelRepository.lockOrCreateForUpdate}), same test shape as
 * {@link StockMovementConcurrencyIntegrationTest}, just exercising
 * {@link StockReservationService#reserve} instead of a stock movement.
 */
@SpringBootTest
@Testcontainers
class StockReservationConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.cache.type", () -> "simple");
        // Many test classes share one real local Redis across a single test run under
        // the same mocked JWT subject - without this, their cumulative request count
        // can cross the production default and spuriously trip ApiRateLimitFilter.
        registry.add("app.rate-limit.api-wide.max-requests", () -> 1_000_000);
    }

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private WarehouseService warehouseService;

    @Autowired
    private StockMovementRecordingService stockMovementRecordingService;

    @Autowired
    private StockReservationService stockReservationService;

    @Autowired
    private StockLevelService stockLevelService;

    @Test
    void given_tenOnHand_when_twoReservationsOfSixRaceConcurrently_then_exactlyOneSucceeds() throws Exception {
        // Given
        Category category = categoryService.create("Electronics", null);
        Product product = productService.create("SKU-1", "Widget", null, category.getId(), UnitOfMeasure.EACH);
        Warehouse warehouse = warehouseService.create("Main Warehouse", null);
        stockMovementRecordingService.recordSingleMovement(
                product.getId(), warehouse.getId(), StockMovementType.RECEIPT, 10, null, Instant.now());

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientStockCount = new AtomicInteger();
        CyclicBarrier bothThreadsReady = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable reserveSix = () -> {
            try {
                bothThreadsReady.await(10, TimeUnit.SECONDS);
                stockReservationService.reserve(product.getId(), warehouse.getId(), 6);
                successCount.incrementAndGet();
            } catch (InsufficientStockException expected) {
                insufficientStockCount.incrementAndGet();
            } catch (Exception unexpected) {
                throw new AssertionError("Unexpected failure in concurrent reservation", unexpected);
            }
        };

        // When
        Future<?> first = executor.submit(reserveSix);
        Future<?> second = executor.submit(reserveSix);
        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(insufficientStockCount.get()).isEqualTo(1);

        int quantityReserved = stockLevelService
                .list(new StockLevelFilter(product.getId(), warehouse.getId()), 1, 20)
                .items()
                .get(0)
                .getQuantityReserved();
        // Only the one reservation that won the race, never both double-applied against the
        // same stale "10 - 0 = 10 available" read.
        assertThat(quantityReserved).isEqualTo(6);
    }
}
