package com.jm.inventory.api.alerting;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * why the JWT is mocked and the Testcontainers Postgres is real. Covers the join logic in
 * {@code StockAlertSpringDataRepository} directly - a threshold with no matching
 * {@code StockLevel} row, a threshold above/below/at the current quantity, and "no
 * threshold at all" never alerting regardless of how low the real stock is.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class StockAlertControllerIntegrationTest {

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

    @BeforeEach
    void setUpAuthenticatedJwts() {
        // MANAGER - this suite's helpers create reorder thresholds, which require at least
        // MANAGER per docs/specs/06-roles-and-permissions/requirements.md; the controller
        // under test here (GET /stock-alerts) itself only requires STAFF.
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
    }

    @Test
    void given_noAuthentication_when_listingAlerts_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/stock-alerts")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_noThresholdConfigured_when_stockIsZero_then_noAlertAppears() throws Exception {
        createProduct();
        createWarehouse();

        mockMvc.perform(get("/api/v1/stock-alerts").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void given_aThresholdWithNoStockLevelRowYet_when_listingAlerts_then_appearsWithZeroQuantityOnHand()
            throws Exception {
        String productId = createProduct();
        String warehouseId = createWarehouse();
        createThreshold(productId, warehouseId, 5, 20);

        mockMvc.perform(get("/api/v1/stock-alerts").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(0)))
                .andExpect(jsonPath("$.items[0].reorderPoint", is(5)))
                .andExpect(jsonPath("$.items[0].reorderQuantity", is(20)));
    }

    @Test
    void given_stockAboveReorderPoint_when_listingAlerts_then_doesNotAppear() throws Exception {
        String productId = createProduct();
        String warehouseId = createWarehouse();
        createThreshold(productId, warehouseId, 5, 20);
        receiveStock(productId, warehouseId, 10);

        mockMvc.perform(get("/api/v1/stock-alerts").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void given_stockExactlyAtReorderPoint_when_listingAlerts_then_appears() throws Exception {
        String productId = createProduct();
        String warehouseId = createWarehouse();
        createThreshold(productId, warehouseId, 5, 20);
        receiveStock(productId, warehouseId, 5);

        mockMvc.perform(get("/api/v1/stock-alerts").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(5)));
    }

    @Test
    void given_aWarehouseFilter_when_listingAlerts_then_onlyThatWarehousesAlertsReturned() throws Exception {
        String productId = createProduct();
        String warehouseA = createWarehouse();
        String warehouseB = createWarehouse();
        createThreshold(productId, warehouseA, 5, 20);
        createThreshold(productId, warehouseB, 5, 20);

        mockMvc.perform(get("/api/v1/stock-alerts?warehouseId=" + warehouseA).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].warehouseId", is(warehouseA)));
    }

    private void createThreshold(String productId, String warehouseId, int reorderPoint, int reorderQuantity)
            throws Exception {
        mockMvc.perform(post("/api/v1/reorder-thresholds").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"reorderPoint\":" + reorderPoint + ",\"reorderQuantity\":" + reorderQuantity + "}"));
    }

    private void receiveStock(String productId, String warehouseId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"type\":\"RECEIPT\",\"quantity\":" + quantity + "}"));
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
