package com.jm.inventory.api.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.jm.inventory.application.alerting.ReorderThresholdService;
import com.jm.inventory.application.catalog.CategoryService;
import com.jm.inventory.application.catalog.ProductService;
import com.jm.inventory.application.customer.CustomerService;
import com.jm.inventory.application.cyclecount.CycleCountService;
import com.jm.inventory.application.purchasing.CreatePurchaseOrderLineCommand;
import com.jm.inventory.application.purchasing.PurchaseOrderService;
import com.jm.inventory.application.sales.CreateSalesOrderLineCommand;
import com.jm.inventory.application.sales.SalesOrderService;
import com.jm.inventory.application.stock.StockMovementRecordingService;
import com.jm.inventory.application.supplier.SupplierService;
import com.jm.inventory.application.warehouse.WarehouseService;
import com.jm.inventory.domain.catalog.Category;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.UnitOfMeasure;
import com.jm.inventory.domain.cyclecount.CycleCount;
import com.jm.inventory.domain.customer.Customer;
import com.jm.inventory.domain.purchasing.PurchaseOrder;
import com.jm.inventory.domain.sales.SalesOrder;
import com.jm.inventory.domain.stock.StockMovementType;
import com.jm.inventory.domain.supplier.Supplier;
import com.jm.inventory.domain.warehouse.Warehouse;
import com.jm.inventory.domain.webhooks.WebhookEventType;
import com.jm.inventory.infrastructure.webhooks.persistence.WebhookOutboxEventJpaEntity;
import com.jm.inventory.infrastructure.webhooks.persistence.WebhookOutboxEventSpringDataRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import com.jm.inventory.api.support.TruncateBusinessTablesExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the four trigger points in docs/specs/14-webhooks/requirements.md write exactly the
 * outbox row they should - nothing more, nothing less - by driving the real application
 * services against a real Testcontainers Postgres and inspecting
 * {@code webhook_outbox_events} directly. Delivery itself ({@code WebhookDispatchService})
 * is unit-tested separately with mocked collaborators - this test is about the write path
 * only.
 */
@SpringBootTest
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class WebhookEventPublishingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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
    private SupplierService supplierService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private ReorderThresholdService reorderThresholdService;
    @Autowired
    private StockMovementRecordingService stockMovementRecordingService;
    @Autowired
    private SalesOrderService salesOrderService;
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private CycleCountService cycleCountService;
    @Autowired
    private WebhookOutboxEventSpringDataRepository webhookOutboxEventSpringDataRepository;

    @Test
    void given_stockCrossingBelowReorderPoint_when_shipped_then_oneStockAlertActivatedEvent() {
        // Given
        Category category = categoryService.create("Electronics", null);
        Product product = productService.create("SKU-1", "Widget", null, category.getId(), UnitOfMeasure.EACH);
        Warehouse warehouse = warehouseService.create("Main", null);
        reorderThresholdService.create(product.getId(), warehouse.getId(), 5, 20);
        stockMovementRecordingService.recordSingleMovement(
                product.getId(), warehouse.getId(), StockMovementType.RECEIPT, 10, "seed", Instant.now());

        // When - crosses from 10 (above 5) to 4 (below 5)
        stockMovementRecordingService.recordSingleMovement(
                product.getId(), warehouse.getId(), StockMovementType.SHIPMENT, 6, "ship", Instant.now());
        // And - already below, no new crossing
        stockMovementRecordingService.recordSingleMovement(
                product.getId(), warehouse.getId(), StockMovementType.SHIPMENT, 1, "ship-more", Instant.now());

        // Then
        List<WebhookOutboxEventJpaEntity> events = outboxEventsOfType(WebhookEventType.STOCK_ALERT_ACTIVATED);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).contains("\"quantityOnHand\":4").contains("\"reorderPoint\":5");
    }

    @Test
    void given_aFullyShippedSalesOrder_when_shippingTheLastLine_then_oneSalesOrderShippedEvent() {
        // Given
        Category category = categoryService.create("Electronics", null);
        Product product = productService.create("SKU-2", "Gadget", null, category.getId(), UnitOfMeasure.EACH);
        Warehouse warehouse = warehouseService.create("Main", null);
        Customer customer = customerService.create("Acme", "acme@example.com", null);
        stockMovementRecordingService.recordSingleMovement(
                product.getId(), warehouse.getId(), StockMovementType.RECEIPT, 10, "seed", Instant.now());
        SalesOrder salesOrder = salesOrderService.create(
                customer.getId(), warehouse.getId(), LocalDate.now().plusDays(1),
                List.of(new CreateSalesOrderLineCommand(product.getId(), 5, BigDecimal.TEN)));
        salesOrderService.confirm(salesOrder.getId());

        // When
        salesOrderService.shipLine(salesOrder.getId(), salesOrder.getLines().get(0).getId(), 5);

        // Then
        List<WebhookOutboxEventJpaEntity> events = outboxEventsOfType(WebhookEventType.SALES_ORDER_SHIPPED);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).contains(salesOrder.getId().toString());
    }

    @Test
    void given_aPartiallyShippedSalesOrder_when_shippingLessThanOrdered_then_noEvent() {
        // Given
        Category category = categoryService.create("Electronics", null);
        Product product = productService.create("SKU-3", "Gizmo", null, category.getId(), UnitOfMeasure.EACH);
        Warehouse warehouse = warehouseService.create("Main", null);
        Customer customer = customerService.create("Acme", "acme@example.com", null);
        stockMovementRecordingService.recordSingleMovement(
                product.getId(), warehouse.getId(), StockMovementType.RECEIPT, 10, "seed", Instant.now());
        SalesOrder salesOrder = salesOrderService.create(
                customer.getId(), warehouse.getId(), LocalDate.now().plusDays(1),
                List.of(new CreateSalesOrderLineCommand(product.getId(), 5, BigDecimal.TEN)));
        salesOrderService.confirm(salesOrder.getId());

        // When - ships only part of the line
        salesOrderService.shipLine(salesOrder.getId(), salesOrder.getLines().get(0).getId(), 2);

        // Then
        assertThat(outboxEventsOfType(WebhookEventType.SALES_ORDER_SHIPPED)).isEmpty();
    }

    @Test
    void given_aFullyReceivedPurchaseOrder_when_receivingTheLastLine_then_onePurchaseOrderReceivedEvent() {
        // Given
        Category category = categoryService.create("Electronics", null);
        Product product = productService.create("SKU-4", "Thingamajig", null, category.getId(), UnitOfMeasure.EACH);
        Warehouse warehouse = warehouseService.create("Main", null);
        Supplier supplier = supplierService.create("Acme Supply", "supply@example.com", null);
        PurchaseOrder purchaseOrder = purchaseOrderService.create(
                supplier.getId(), warehouse.getId(), LocalDate.now().plusDays(1),
                List.of(new CreatePurchaseOrderLineCommand(product.getId(), 8, BigDecimal.ONE)));
        purchaseOrderService.place(purchaseOrder.getId());

        // When
        purchaseOrderService.receiveLine(purchaseOrder.getId(), purchaseOrder.getLines().get(0).getId(), 8);

        // Then
        List<WebhookOutboxEventJpaEntity> events = outboxEventsOfType(WebhookEventType.PURCHASE_ORDER_RECEIVED);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).contains(purchaseOrder.getId().toString());
    }

    @Test
    void given_anOpenCycleCount_when_approving_then_oneCycleCountApprovedEvent() {
        // Given
        Category category = categoryService.create("Electronics", null);
        Product product = productService.create("SKU-5", "Doohickey", null, category.getId(), UnitOfMeasure.EACH);
        Warehouse warehouse = warehouseService.create("Main", null);
        stockMovementRecordingService.recordSingleMovement(
                product.getId(), warehouse.getId(), StockMovementType.RECEIPT, 3, "seed", Instant.now());
        CycleCount cycleCount = cycleCountService.create(warehouse.getId(), List.of(product.getId()));
        cycleCountService.recordCount(cycleCount.getId(), cycleCount.getLines().get(0).getId(), 3);

        // When
        cycleCountService.approve(cycleCount.getId());

        // Then
        List<WebhookOutboxEventJpaEntity> events = outboxEventsOfType(WebhookEventType.CYCLE_COUNT_APPROVED);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).contains(cycleCount.getId().toString());
    }

    private List<WebhookOutboxEventJpaEntity> outboxEventsOfType(WebhookEventType eventType) {
        return webhookOutboxEventSpringDataRepository.findAll().stream()
                .filter(entity -> entity.getEventType() == eventType)
                .toList();
    }
}
