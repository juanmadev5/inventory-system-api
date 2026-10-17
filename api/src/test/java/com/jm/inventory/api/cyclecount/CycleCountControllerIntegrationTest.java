package com.jm.inventory.api.cyclecount;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * why the JWT is mocked and the Testcontainers Postgres is real. Proves the full
 * open/count/approve-or-cancel lifecycle, the FULL/SELECTION scope decision, and that
 * approval posts the right adjustment type against the live system quantity - see
 * docs/specs/09-cycle-counts/requirements.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class CycleCountControllerIntegrationTest {

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
    void given_noAuthentication_when_listingCycleCounts_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/cycle-counts")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_anUnknownWarehouseId_when_creatingACycleCount_then_unprocessable() throws Exception {
        mockMvc.perform(post("/api/v1/cycle-counts").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"warehouseId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    void given_noProductIdsGiven_when_creatingACycleCount_then_fullScopeIncludesEveryProductWithStock()
            throws Exception {
        String warehouseId = createWarehouse();
        String productId = createActiveProduct();
        receiveStock(productId, warehouseId, 10);

        mockMvc.perform(post("/api/v1/cycle-counts").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"warehouseId\":\"" + warehouseId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope", is("FULL")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].productId", is(productId)))
                .andExpect(jsonPath("$.lines[0].countedQuantity").doesNotExist());
    }

    @Test
    void given_productIdsGiven_when_creatingACycleCount_then_selectionScopeIncludesOnlyThoseProducts()
            throws Exception {
        String warehouseId = createWarehouse();
        String productId = createActiveProduct();

        mockMvc.perform(post("/api/v1/cycle-counts").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"warehouseId\":\"" + warehouseId + "\",\"productIds\":[\"" + productId + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope", is("SELECTION")))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].productId", is(productId)));
    }

    @Test
    void given_anUnknownProductIdInSelection_when_creating_then_unprocessable() throws Exception {
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/cycle-counts").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"warehouseId\":\"" + warehouseId + "\",\"productIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    void given_aStaffRoleToken_when_recordingACount_then_succeeds() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/lines/" + fixture.lineId
                        + "/count").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"countedQuantity\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].countedQuantity", is(9)));
    }

    @Test
    void given_aStaffRoleToken_when_approving_then_forbidden() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);
        recordCount(fixture.cycleCountId, fixture.lineId, 10);

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/approval")
                        .with(authenticatedAsStaff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_anIncompleteSession_when_approving_then_conflict() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/approval")
                        .with(authenticatedAsManager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CYCLE_COUNT_INCOMPLETE")));
    }

    @Test
    void given_countedHigherThanSystem_when_approved_then_postsAdjustmentIncrease() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);
        recordCount(fixture.cycleCountId, fixture.lineId, 15);

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/approval")
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsStaff))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(15)));
    }

    @Test
    void given_countedLowerThanSystem_when_approved_then_postsAdjustmentDecrease() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);
        recordCount(fixture.cycleCountId, fixture.lineId, 4);

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/approval")
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsStaff))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(4)));
    }

    @Test
    void given_countedEqualToSystem_when_approved_then_stockUnchanged() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);
        recordCount(fixture.cycleCountId, fixture.lineId, 10);

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/approval")
                        .with(authenticatedAsManager))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsStaff))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(10)));
    }

    @Test
    void given_anApprovedSession_when_recordingAnotherCount_then_conflict() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);
        recordCount(fixture.cycleCountId, fixture.lineId, 10);
        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/approval")
                .with(authenticatedAsManager));

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/lines/" + fixture.lineId
                        + "/count").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"countedQuantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CYCLE_COUNT_NOT_OPEN")));
    }

    @Test
    void given_anOpenSession_when_cancelling_then_cancelledWithNoAdjustment() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);
        recordCount(fixture.cycleCountId, fixture.lineId, 999);

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/cancellation")
                        .with(authenticatedAsStaff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsStaff))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(10)));
    }

    @Test
    void given_anApprovedSession_when_cancelling_then_conflict() throws Exception {
        CycleCountFixture fixture = createSelectionCycleCount(10);
        recordCount(fixture.cycleCountId, fixture.lineId, 10);
        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/approval")
                .with(authenticatedAsManager));

        mockMvc.perform(post("/api/v1/cycle-counts/" + fixture.cycleCountId + "/cancellation")
                        .with(authenticatedAsStaff))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CYCLE_COUNT_NOT_OPEN")));
    }

    private void recordCount(String cycleCountId, String lineId, int countedQuantity) throws Exception {
        mockMvc.perform(post("/api/v1/cycle-counts/" + cycleCountId + "/lines/" + lineId + "/count")
                .with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"countedQuantity\":" + countedQuantity + "}"));
    }

    private void receiveStock(String productId, String warehouseId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"productId\":\"" + productId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"type\":\"RECEIPT\",\"quantity\":" + quantity + "}"));
    }

    private CycleCountFixture createSelectionCycleCount(int initialStock) throws Exception {
        String warehouseId = createWarehouse();
        String productId = createActiveProduct();
        receiveStock(productId, warehouseId, initialStock);

        MvcResult createResult = mockMvc
                .perform(post("/api/v1/cycle-counts").with(authenticatedAsStaff)
                        .contentType("application/json")
                        .content("{\"warehouseId\":\"" + warehouseId + "\",\"productIds\":[\"" + productId + "\"]}"))
                .andReturn();
        String cycleCountId = idFromLocation(createResult);
        String lineId = firstLineId(createResult);

        return new CycleCountFixture(cycleCountId, lineId, productId, warehouseId);
    }

    private String createWarehouse() throws Exception {
        return idFromLocation(mockMvc
                .perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Warehouse " + UUID.randomUUID() + "\"}"))
                .andReturn());
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

    private String idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String firstLineId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return JsonPath.read(body, "$.lines[0].id");
    }

    private record CycleCountFixture(String cycleCountId, String lineId, String productId, String warehouseId) {
    }
}
