package com.jm.inventory.api.reporting;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jm.inventory.domain.user.Role;
import java.time.Instant;
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
 * why the JWT is mocked and the Testcontainers Postgres is real. See
 * docs/specs/10-reporting/requirements.md for the deliberate limitations these tests confirm
 * (page-scoped valuation total, null cost for a product with no purchase-order history).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class ReportingControllerIntegrationTest {

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

    private JwtRequestPostProcessor authenticatedAsStaff;
    private JwtRequestPostProcessor authenticatedAsManager;

    @BeforeEach
    void setUpAuthenticatedJwts() {
        authenticatedAsStaff = jwt()
                .authorities(new SimpleGrantedAuthority(Role.STAFF.authorityName()));
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
    }

    @Test
    void given_noAuthentication_when_gettingValuation_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory-valuation")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aStaffRoleToken_when_gettingValuation_then_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory-valuation").with(authenticatedAsStaff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_aProductWithPurchaseOrderHistory_when_gettingValuation_then_computesAverageCostAndValue()
            throws Exception {
        String warehouseId = createWarehouse();
        String productId = createProduct();
        receiveStock(productId, warehouseId, 10);
        createAndReceivePurchaseOrderLine(warehouseId, productId, 5, "4.00");
        createAndReceivePurchaseOrderLine(warehouseId, productId, 5, "6.00");

        mockMvc.perform(get("/api/v1/reports/inventory-valuation?warehouseId=" + warehouseId)
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId", is(productId)))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(20)))
                .andExpect(jsonPath("$.items[0].unitCost", is(5.0)))
                .andExpect(jsonPath("$.items[0].value", is(100.0)))
                .andExpect(jsonPath("$.pageTotalValue", is(100.0)))
                .andExpect(jsonPath("$.excludedCount", is(0)));
    }

    @Test
    void given_aProductWithNoPurchaseOrderHistory_when_gettingValuation_then_nullCostAndExcluded() throws Exception {
        String warehouseId = createWarehouse();
        String productId = createProduct();
        receiveStock(productId, warehouseId, 10);

        mockMvc.perform(get("/api/v1/reports/inventory-valuation?warehouseId=" + warehouseId)
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitCost", is(nullValue())))
                .andExpect(jsonPath("$.items[0].value", is(nullValue())))
                .andExpect(jsonPath("$.pageTotalValue", is(0)))
                .andExpect(jsonPath("$.excludedCount", is(1)));
    }

    @Test
    void given_movementsOfVariousTypes_when_gettingMovementSummary_then_countsAndQuantitiesCorrect()
            throws Exception {
        String warehouseId = createWarehouse();
        String productId = createProduct();
        receiveStock(productId, warehouseId, 10);
        shipStock(productId, warehouseId, 4);

        mockMvc.perform(get("/api/v1/reports/stock-movement-summary?warehouseId=" + warehouseId
                        + "&productId=" + productId).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalsByType.RECEIPT.count", is(1)))
                .andExpect(jsonPath("$.totalsByType.RECEIPT.totalQuantity", is(10)))
                .andExpect(jsonPath("$.totalsByType.SHIPMENT.count", is(1)))
                .andExpect(jsonPath("$.totalsByType.SHIPMENT.totalQuantity", is(4)))
                .andExpect(jsonPath("$.totalsByType.TRANSFER_IN.count", is(0)))
                .andExpect(jsonPath("$.totalsByType.TRANSFER_IN.totalQuantity", is(0)))
                .andExpect(jsonPath("$.totalsByType.ADJUSTMENT_INCREASE.count", is(0)));
    }

    @Test
    void given_anOccurredRangeExcludingTheMovement_when_gettingMovementSummary_then_notCounted() throws Exception {
        String warehouseId = createWarehouse();
        String productId = createProduct();
        receiveStock(productId, warehouseId, 10);

        String farFuture = Instant.now().plusSeconds(3600).toString();

        mockMvc.perform(get("/api/v1/reports/stock-movement-summary?warehouseId=" + warehouseId
                        + "&productId=" + productId + "&occurredFrom=" + farFuture).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalsByType.RECEIPT.count", is(0)));
    }

    @Test
    void given_aProductWithAMovement_when_gettingStockAging_then_lastMovementAtAndDaysSinceComputed()
            throws Exception {
        String warehouseId = createWarehouse();
        String productId = createProduct();
        receiveStock(productId, warehouseId, 10);

        mockMvc.perform(get("/api/v1/reports/stock-aging?warehouseId=" + warehouseId).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId", is(productId)))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(10)))
                .andExpect(jsonPath("$.items[0].lastMovementAt", is(notNullValue())))
                .andExpect(jsonPath("$.items[0].daysSinceLastMovement", is(0)));
    }

    private void receiveStock(String productId, String warehouseId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"type\":\"RECEIPT\",\"quantity\":" + quantity + "}"));
    }

    private void shipStock(String productId, String warehouseId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"type\":\"SHIPMENT\",\"quantity\":" + quantity + "}"));
    }

    private void createAndReceivePurchaseOrderLine(String warehouseId, String productId, int quantity, String unitCost)
            throws Exception {
        String supplierId = idFromLocation(mockMvc
                .perform(post("/api/v1/suppliers").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Supplier " + UUID.randomUUID() + "\"}"))
                .andReturn());

        MvcResult createResult = mockMvc
                .perform(post("/api/v1/purchase-orders").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"supplierId\":\"" + supplierId + "\",\"warehouseId\":\"" + warehouseId
                                + "\",\"lines\":[{\"productId\":\"" + productId + "\",\"quantityOrdered\":" + quantity
                                + ",\"unitCost\":" + unitCost + "}]}"))
                .andReturn();
        String purchaseOrderId = idFromLocation(createResult);
        String lineId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.lines[0].id");

        mockMvc.perform(post("/api/v1/purchase-orders/" + purchaseOrderId + "/placement")
                .with(authenticatedAsManager));
        mockMvc.perform(post("/api/v1/purchase-orders/" + purchaseOrderId + "/lines/" + lineId + "/receipts")
                .with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"quantity\":" + quantity + "}"));
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
}
