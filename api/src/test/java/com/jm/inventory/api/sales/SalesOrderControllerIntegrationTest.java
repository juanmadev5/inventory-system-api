package com.jm.inventory.api.sales;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jm.inventory.domain.user.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import com.jm.inventory.api.support.TruncateBusinessTablesExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * See {@link com.jm.inventory.api.catalog.CategoryControllerIntegrationTest}'s Javadoc for
 * why the JWT is mocked and the Testcontainers Postgres is real. Also proves this feature's
 * reservation mechanism: confirming reserves {@code quantityReserved}, shipping releases it
 * and decreases {@code quantityOnHand} via the shared stock-movements recording service - see
 * docs/specs/08-sales-orders-and-customers/requirements.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class SalesOrderControllerIntegrationTest {

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
    private MockMvc mockMvc;

    private JwtRequestPostProcessor authenticatedAsManager;
    private JwtRequestPostProcessor authenticatedAsStaff;

    @BeforeEach
    void setUpAuthenticatedJwts() {
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
        authenticatedAsStaff = jwt()
                .authorities(new SimpleGrantedAuthority(Role.STAFF.authorityName()));
    }

    @Test
    void given_noAuthentication_when_listingSalesOrders_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/sales-orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aStaffRoleToken_when_creatingASalesOrder_then_forbidden() throws Exception {
        String customerId = createCustomer();
        String warehouseId = createWarehouse();
        String productId = createProduct();

        mockMvc.perform(post("/api/v1/sales-orders").with(authenticatedAsStaff)
                .contentType("application/json")
                .content(salesOrderJson(customerId, warehouseId, productId, 10, "9.99")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_anUnknownCustomerId_when_creatingASalesOrder_then_unprocessable() throws Exception {
        String warehouseId = createWarehouse();
        String productId = createProduct();

        mockMvc.perform(post("/api/v1/sales-orders").with(authenticatedAsManager)
                .contentType("application/json")
                .content(salesOrderJson(UUID.randomUUID().toString(), warehouseId, productId, 10, "9.99")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("customerId")));
    }

    @Test
    void given_anUnknownProductIdInALine_when_creatingASalesOrder_then_unprocessable() throws Exception {
        String customerId = createCustomer();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/sales-orders").with(authenticatedAsManager)
                .contentType("application/json")
                .content(salesOrderJson(customerId, warehouseId, UUID.randomUUID().toString(), 10, "9.99")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("productId")));
    }

    @Test
    void given_noLines_when_creatingASalesOrder_then_badRequest() throws Exception {
        String customerId = createCustomer();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/sales-orders").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"customerId\":\"" + customerId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"lines\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_aValidRequest_when_creatingASalesOrder_then_createdInDraftStatus() throws Exception {
        String customerId = createCustomer();
        String warehouseId = createWarehouse();
        String productId = createProduct();

        mockMvc.perform(post("/api/v1/sales-orders").with(authenticatedAsManager)
                .contentType("application/json")
                .content(salesOrderJson(customerId, warehouseId, productId, 10, "9.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.lines[0].quantityOrdered", is(10)))
                .andExpect(jsonPath("$.lines[0].quantityShipped", is(0)));
    }

    @Test
    void given_enoughStock_when_confirmingADraftOrder_then_confirmedAndStockReserved() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/confirmation")
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(10)))
                .andExpect(jsonPath("$.items[0].quantityReserved", is(10)));
    }

    @Test
    void given_insufficientStock_when_confirming_then_conflictAndNothingReserved() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 4);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/confirmation")
                        .with(authenticatedAsManager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INSUFFICIENT_STOCK")));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityReserved", is(0)));
    }

    @Test
    void given_aConfirmedOrder_when_confirmingItAgain_then_conflict() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);
        confirm(fixture.salesOrderId);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/confirmation")
                        .with(authenticatedAsManager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SALES_ORDER_NOT_DRAFT")));
    }

    @Test
    void given_aDraftOrder_when_patchingExpectedShipDate_then_updated() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);

        mockMvc.perform(patch("/api/v1/sales-orders/" + fixture.salesOrderId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"expectedShipDate\":\"2026-12-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedShipDate", is("2026-12-01")));
    }

    @Test
    void given_aConfirmedOrder_when_patchingExpectedShipDate_then_conflict() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);
        confirm(fixture.salesOrderId);

        mockMvc.perform(patch("/api/v1/sales-orders/" + fixture.salesOrderId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"expectedShipDate\":\"2026-12-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SALES_ORDER_NOT_EDITABLE")));
    }

    @Test
    void given_aDraftOrder_when_shippingALine_then_conflict() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/lines/" + fixture.lineId
                        + "/shipments").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"quantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SALES_ORDER_NOT_CONFIRMED")));
    }

    @Test
    void given_aConfirmedOrder_when_shippingPartialQuantity_then_staysConfirmedAndReservationPartiallyReleases()
            throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);
        confirm(fixture.salesOrderId);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/lines/" + fixture.lineId
                        + "/shipments").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.lines[0].quantityShipped", is(4)));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(6)))
                .andExpect(jsonPath("$.items[0].quantityReserved", is(6)));
    }

    @Test
    void given_fullyShippingAnOrder_then_autoTransitionsToShippedAndStockLevelReflectsIt() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);
        confirm(fixture.salesOrderId);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/lines/" + fixture.lineId
                        + "/shipments").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"quantity\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SHIPPED")))
                .andExpect(jsonPath("$.lines[0].quantityShipped", is(10)));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(0)))
                .andExpect(jsonPath("$.items[0].quantityReserved", is(0)));
    }

    @Test
    void given_aShipmentExceedingOrderedQuantity_when_shipped_then_conflict() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);
        confirm(fixture.salesOrderId);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/lines/" + fixture.lineId
                        + "/shipments").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"quantity\":11}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SHIPMENT_EXCEEDS_ORDERED_QUANTITY")));
    }

    @Test
    void given_anUnknownLineId_when_shipping_then_notFound() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);
        confirm(fixture.salesOrderId);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/lines/" + UUID.randomUUID()
                        + "/shipments").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void given_aDraftOrder_when_cancelling_then_cancelled() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/cancellation")
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void given_aConfirmedOrder_when_cancelling_then_cancelledAndReservationReleased() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);
        confirm(fixture.salesOrderId);

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/cancellation")
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(10)))
                .andExpect(jsonPath("$.items[0].quantityReserved", is(0)));
    }

    @Test
    void given_aShippedOrder_when_cancelling_then_conflict() throws Exception {
        SalesOrderFixture fixture = createDraftSalesOrder(10);
        receiveStock(fixture.productId, fixture.warehouseId, 10);
        confirm(fixture.salesOrderId);
        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/lines/" + fixture.lineId
                        + "/shipments").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"quantity\":10}"));

        mockMvc.perform(post("/api/v1/sales-orders/" + fixture.salesOrderId + "/cancellation")
                        .with(authenticatedAsManager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SALES_ORDER_NOT_CANCELLABLE")));
    }

    private void confirm(String salesOrderId) throws Exception {
        mockMvc.perform(post("/api/v1/sales-orders/" + salesOrderId + "/confirmation").with(authenticatedAsManager));
    }

    private void receiveStock(String productId, String warehouseId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"type\":\"RECEIPT\",\"quantity\":" + quantity + "}"));
    }

    private SalesOrderFixture createDraftSalesOrder(int quantityOrdered) throws Exception {
        String customerId = createCustomer();
        String warehouseId = createWarehouse();
        String productId = createProduct();

        MvcResult createResult = mockMvc
                .perform(post("/api/v1/sales-orders").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content(salesOrderJson(customerId, warehouseId, productId, quantityOrdered, "9.99")))
                .andReturn();
        String salesOrderId = idFromLocation(createResult);
        String lineId = firstLineId(createResult);

        return new SalesOrderFixture(salesOrderId, lineId, productId, warehouseId);
    }

    private String salesOrderJson(String customerId, String warehouseId, String productId, int quantity, String unitPrice) {
        return "{\"customerId\":\"" + customerId + "\",\"warehouseId\":\"" + warehouseId
                + "\",\"lines\":[{\"productId\":\"" + productId + "\",\"quantityOrdered\":" + quantity
                + ",\"unitPrice\":" + unitPrice + "}]}";
    }

    private String createCustomer() throws Exception {
        return idFromLocation(mockMvc
                .perform(post("/api/v1/customers").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Customer " + UUID.randomUUID() + "\"}"))
                .andReturn());
    }

    private String createWarehouse() throws Exception {
        return idFromLocation(mockMvc
                .perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Warehouse " + UUID.randomUUID() + "\"}"))
                .andReturn());
    }

    private String createProduct() throws Exception {
        String categoryId = idFromLocation(mockMvc
                .perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Electronics\"}"))
                .andReturn());

        return idFromLocation(mockMvc
                .perform(post("/api/v1/products").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"sku\":\"SKU-1\",\"name\":\"Widget\",\"categoryId\":\"" + categoryId
                                + "\",\"unitOfMeasure\":\"EACH\"}"))
                .andReturn());
    }

    private String idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String firstLineId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return JsonPath.read(body, "$.lines[0].id");
    }

    private record SalesOrderFixture(String salesOrderId, String lineId, String productId, String warehouseId) {
    }
}
