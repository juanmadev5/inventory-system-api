package com.jm.inventory.api.stock;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * why the JWT is mocked and the Testcontainers Postgres is real - same scope here, applied
 * to {@code /api/v1/stock-movements}. The dedicated concurrency guarantee (the pessimistic
 * lock on {@code StockLevel}) has its own test -
 * {@link StockMovementConcurrencyIntegrationTest} - since no sequential test can prove it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class StockMovementControllerIntegrationTest {

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
    private JwtRequestPostProcessor authenticatedAsAdmin;

    @BeforeEach
    void setUpAuthenticatedJwts() {
        // MANAGER - this suite's helpers create categories/products/warehouses, which
        // require at least MANAGER; RECEIPT/SHIPMENT/transfers themselves only require
        // STAFF, a bare ADJUSTMENT_INCREASE/ADJUSTMENT_DECREASE requires ADMIN - see
        // docs/specs/06-roles-and-permissions/requirements.md.
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
        authenticatedAsAdmin = jwt()
                .authorities(new SimpleGrantedAuthority(Role.ADMIN.authorityName()));
    }

    @Test
    void given_noAuthentication_when_listingMovements_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/stock-movements")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aManagerRoleToken_when_recordingABareAdjustment_then_forbidden() throws Exception {
        String productId = createActiveProduct();
        String warehouseId = createWarehouse();
        recordMovement(productId, warehouseId, "RECEIPT", 10);

        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content(movementJson(productId, warehouseId, "ADJUSTMENT_DECREASE", 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_anAdminRoleToken_when_recordingABareAdjustment_then_succeeds() throws Exception {
        String productId = createActiveProduct();
        String warehouseId = createWarehouse();
        recordMovement(productId, warehouseId, "RECEIPT", 10);

        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsAdmin)
                .contentType("application/json")
                .content(movementJson(productId, warehouseId, "ADJUSTMENT_DECREASE", 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("ADJUSTMENT_DECREASE")));
    }

    @Test
    void given_aReceipt_when_recorded_then_stockLevelIncreases() throws Exception {
        String productId = createActiveProduct();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content(movementJson(productId, warehouseId, "RECEIPT", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("RECEIPT")))
                .andExpect(jsonPath("$.quantity", is(10)));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + productId + "&warehouseId=" + warehouseId)
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(10)));
    }

    @Test
    void given_aShipmentExceedingOnHandQuantity_when_recorded_then_insufficientStock() throws Exception {
        String productId = createActiveProduct();
        String warehouseId = createWarehouse();
        recordMovement(productId, warehouseId, "RECEIPT", 5);

        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content(movementJson(productId, warehouseId, "SHIPMENT", 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INSUFFICIENT_STOCK")));
    }

    @Test
    void given_anInactiveProduct_when_receivingStock_then_productInactive() throws Exception {
        String productId = createActiveProduct();
        String warehouseId = createWarehouse();
        mockMvc.perform(patch("/api/v1/products/" + productId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"active\":false}"));

        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content(movementJson(productId, warehouseId, "RECEIPT", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_INACTIVE")));
    }

    @Test
    void given_anInactiveWarehouse_when_recordingAMovement_then_warehouseInactive() throws Exception {
        String productId = createActiveProduct();
        String warehouseId = createWarehouse();
        mockMvc.perform(patch("/api/v1/warehouses/" + warehouseId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"active\":false}"));

        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content(movementJson(productId, warehouseId, "RECEIPT", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("WAREHOUSE_INACTIVE")));
    }

    @Test
    void given_anUnknownProductId_when_recordingAMovement_then_unprocessable() throws Exception {
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content(movementJson(UUID.randomUUID().toString(), warehouseId, "RECEIPT", 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("productId")));
    }

    @Test
    void given_aTransferTypeSubmittedDirectly_when_recordingAMovement_then_badRequest() throws Exception {
        String productId = createActiveProduct();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content(movementJson(productId, warehouseId, "TRANSFER_IN", 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_sameSourceAndDestinationWarehouse_when_transferring_then_unprocessable() throws Exception {
        String productId = createActiveProduct();
        String warehouseId = createWarehouse();

        String requestBody = "{\"productId\":\"" + productId + "\",\"sourceWarehouseId\":\"" + warehouseId
                + "\",\"destinationWarehouseId\":\"" + warehouseId + "\",\"quantity\":1}";

        mockMvc.perform(post("/api/v1/stock-movements/transfers").with(authenticatedAsManager)
                .contentType("application/json")
                .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("destinationWarehouseId")));
    }

    @Test
    void given_aValidTransfer_when_recorded_then_bothWarehousesUpdatedAndMovementsLinked() throws Exception {
        String productId = createActiveProduct();
        String sourceWarehouseId = createWarehouse();
        String destinationWarehouseId = createWarehouse();
        recordMovement(productId, sourceWarehouseId, "RECEIPT", 10);

        String requestBody = "{\"productId\":\"" + productId + "\",\"sourceWarehouseId\":\"" + sourceWarehouseId
                + "\",\"destinationWarehouseId\":\"" + destinationWarehouseId + "\",\"quantity\":4}";

        mockMvc.perform(post("/api/v1/stock-movements/transfers").with(authenticatedAsManager)
                .contentType("application/json")
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outboundMovement.type", is("TRANSFER_OUT")))
                .andExpect(jsonPath("$.outboundMovement.relatedMovementId", is(nullValue())))
                .andExpect(jsonPath("$.inboundMovement.type", is("TRANSFER_IN")));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + productId + "&warehouseId=" + sourceWarehouseId)
                        .with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(6)));
        mockMvc.perform(get("/api/v1/stock-levels?productId=" + productId + "&warehouseId=" + destinationWarehouseId)
                        .with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(4)));
    }

    @Test
    void given_anUnknownMovementId_when_gettingIt_then_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/stock-movements/" + UUID.randomUUID()).with(authenticatedAsManager))
                .andExpect(status().isNotFound());
    }

    private void recordMovement(String productId, String warehouseId, String type, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content(movementJson(productId, warehouseId, type, quantity)));
    }

    private String movementJson(String productId, String warehouseId, String type, int quantity) {
        return "{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId + "\",\"type\":\"" + type
                + "\",\"quantity\":" + quantity + "}";
    }

    private String createActiveProduct() throws Exception {
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

    private String createWarehouse() throws Exception {
        return idFromLocation(mockMvc
                .perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Warehouse " + UUID.randomUUID() + "\"}"))
                .andReturn());
    }

    private String idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
