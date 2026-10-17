package com.jm.inventory.api.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.jm.inventory.application.catalog.CategoryService;
import com.jm.inventory.application.catalog.ProductService;
import com.jm.inventory.application.stock.StockLevelService;
import com.jm.inventory.application.stock.StockMovementRecordingService;
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
 * Proves docs/specs/02-stock-movements/requirements.md's concurrency criterion: two
 * movements racing against the same {@code (productId, warehouseId)} must not both read the
 * same stale quantity and both succeed when only one should. Runs two real, concurrent
 * transactions on separate HikariCP connections (pool size raised above the default so the
 * second thread isn't merely queued behind the first for a connection - it's the
 * {@code SELECT ... FOR UPDATE} in {@code StockLevelRepositoryAdapter} that must serialize
 * them, not connection-pool contention).
 */
@SpringBootTest
@Testcontainers
class StockMovementConcurrencyIntegrationTest {

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
    private StockLevelService stockLevelService;

    @Test
    void given_tenQuantityOnHand_when_twoShipmentsOfSixRaceConcurrently_then_exactlyOneSucceeds()
            throws Exception {
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

        Runnable shipSix = () -> {
            try {
                bothThreadsReady.await(10, TimeUnit.SECONDS);
                stockMovementRecordingService.recordSingleMovement(
                        product.getId(), warehouse.getId(), StockMovementType.SHIPMENT, 6, null, Instant.now());
                successCount.incrementAndGet();
            } catch (InsufficientStockException expected) {
                insufficientStockCount.incrementAndGet();
            } catch (Exception unexpected) {
                throw new AssertionError("Unexpected failure in concurrent shipment", unexpected);
            }
        };

        // When
        Future<?> first = executor.submit(shipSix);
        Future<?> second = executor.submit(shipSix);
        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(insufficientStockCount.get()).isEqualTo(1);

        int quantityOnHand = stockLevelService
                .list(new StockLevelFilter(product.getId(), warehouse.getId()), 1, 20)
                .items()
                .get(0)
                .getQuantityOnHand();
        // 10 (RECEIPT) - 6 (the one shipment that won the race) = 4 - never negative, never
        // double-decremented by both threads reading the same stale "10".
        assertThat(quantityOnHand).isEqualTo(4);
    }
}
