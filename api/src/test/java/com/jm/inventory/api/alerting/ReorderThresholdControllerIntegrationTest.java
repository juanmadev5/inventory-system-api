package com.jm.inventory.api.alerting;

import static org.hamcrest.Matchers.is;
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
 * why the JWT is mocked and the Testcontainers Postgres is real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class ReorderThresholdControllerIntegrationTest {

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
        // MANAGER - defining a reorder threshold requires at least MANAGER, per
        // docs/specs/06-roles-and-permissions/requirements.md.
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
        authenticatedAsStaff = jwt()
                .authorities(new SimpleGrantedAuthority(Role.STAFF.authorityName()));
    }

    @Test
    void given_noAuthentication_when_listingThresholds_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reorder-thresholds")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aStaffRoleToken_when_creatingAThreshold_then_forbidden() throws Exception {
        String productId = createProduct();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/reorder-thresholds").with(authenticatedAsStaff)
                .contentType("application/json")
                .content(thresholdJson(productId, warehouseId, 10, 20)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_anUnknownProductId_when_creatingAThreshold_then_unprocessable() throws Exception {
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/reorder-thresholds").with(authenticatedAsManager)
                .contentType("application/json")
                .content(thresholdJson(UUID.randomUUID().toString(), warehouseId, 10, 20)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("productId")));
    }

    @Test
    void given_aValidRequest_when_creatingAThreshold_then_createdAndRetrievable() throws Exception {
        String productId = createProduct();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/reorder-thresholds").with(authenticatedAsManager)
                .contentType("application/json")
                .content(thresholdJson(productId, warehouseId, 10, 20)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reorderPoint", is(10)))
                .andExpect(jsonPath("$.reorderQuantity", is(20)));
    }

    @Test
    void given_aThresholdAlreadyConfigured_when_creatingAnotherForTheSamePair_then_conflict() throws Exception {
        String productId = createProduct();
        String warehouseId = createWarehouse();
        mockMvc.perform(post("/api/v1/reorder-thresholds").with(authenticatedAsManager)
                .contentType("application/json")
                .content(thresholdJson(productId, warehouseId, 10, 20)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reorder-thresholds").with(authenticatedAsManager)
                .contentType("application/json")
                .content(thresholdJson(productId, warehouseId, 5, 15)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("REORDER_THRESHOLD_ALREADY_EXISTS")));
    }

    @Test
    void given_aNegativeReorderPoint_when_creatingAThreshold_then_badRequest() throws Exception {
        String productId = createProduct();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/reorder-thresholds").with(authenticatedAsManager)
                .contentType("application/json")
                .content(thresholdJson(productId, warehouseId, -1, 20)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_anExistingThreshold_when_patchingOnlyReorderPoint_then_reorderQuantityUnchanged() throws Exception {
        String productId = createProduct();
        String warehouseId = createWarehouse();
        MvcResult createResult = mockMvc
                .perform(post("/api/v1/reorder-thresholds").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content(thresholdJson(productId, warehouseId, 10, 20)))
                .andReturn();
        String thresholdId = idFromLocation(createResult);

        mockMvc.perform(patch("/api/v1/reorder-thresholds/" + thresholdId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"reorderPoint\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reorderPoint", is(3)))
                .andExpect(jsonPath("$.reorderQuantity", is(20)));
    }

    private String thresholdJson(String productId, String warehouseId, int reorderPoint, int reorderQuantity) {
        return "{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId + "\",\"reorderPoint\":"
                + reorderPoint + ",\"reorderQuantity\":" + reorderQuantity + "}";
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
